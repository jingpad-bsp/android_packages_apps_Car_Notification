/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.notification;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Notification Manager for heads-up notifications in car.
 */
public class CarHeadsUpNotificationManager
        implements CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
    private static CarHeadsUpNotificationManager sManager;
    private final Context mContext;
    private final boolean mEnableMediaNotification;
    private final boolean mEnableOngoingNotification;
    private final long mDuration;
    private final long mEnterAnimationDuration;
    private final int mScrimHeightBelowNotification;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private final KeyguardManager mKeyguardManager;
    private final PreprocessingManager mPreprocessingManager;
    private final NotificationManager mNotificationManager;
    private final WindowManager mWindowManager;
    private final LayoutInflater mInflater;
    private final Handler mTimer;
    private final View mScrimView;
    private final FrameLayout mWrapper;
    private StatusBarNotification mVisibleNotification;
    private boolean mShouldRestrictMessagePreview;

    private CarHeadsUpNotificationManager(Context context) {
        mContext = context.getApplicationContext();
        mEnableMediaNotification =
                context.getResources().getBoolean(R.bool.config_showMediaNotification);
        mEnableOngoingNotification =
                context.getResources().getBoolean(R.bool.config_showOngoingNotification);
        mDuration = mContext.getResources().getInteger(R.integer.headsup_notification_duration_ms);
        mEnterAnimationDuration =
                mContext.getResources().getInteger(R.integer.headsup_enter_duration_ms);
        mScrimHeightBelowNotification = mContext.getResources().getDimensionPixelOffset(
                R.dimen.headsup_scrim_height_below_notification);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        mPreprocessingManager = PreprocessingManager.getInstance(context);
        mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mWindowManager =
                (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mInflater = LayoutInflater.from(mContext);
        mTimer = new Handler();

        // The reason we are adding the gradient scrim as its own window is because
        // we want the touch events to work for notifications, but not the gradient scrim.
        WindowManager.LayoutParams scrimParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                // This type allows covering status bar but not receiving touch input
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        scrimParams.gravity = Gravity.TOP;
        mScrimView = new View(mContext);
        mScrimView.setBackgroundResource(R.drawable.headsup_scrim);
        mScrimView.setVisibility(View.GONE);
        mWindowManager.addView(mScrimView, scrimParams);

        WindowManager.LayoutParams wrapperParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                // This type allows covering status bar and receiving touch input
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        wrapperParams.gravity = Gravity.TOP;
        mWrapper = new FrameLayout(mContext);
        mWrapper.setPadding(
                0, mContext.getResources().getDimensionPixelOffset(R.dimen.car_padding_1), 0, 0);
        mWindowManager.addView(mWrapper, wrapperParams);
    }

    /**
     * Show the notification as a heads-up if it meets the criteria.
     */
    public void maybeShowHeadsUp(
            StatusBarNotification statusBarNotification,
            NotificationListenerService.RankingMap rankingMap) {
        if (!shouldShowHeadsUp(statusBarNotification, rankingMap)) {
            return;
        }
        showHeadsUp(mPreprocessingManager.optimizeForDriving(statusBarNotification));
    }

    private void showHeadsUp(StatusBarNotification statusBarNotification) {
        // Remove previous heads-up notifications immediately as well as the previous timer
        mWrapper.removeAllViews();
        mTimer.removeCallbacksAndMessages(null);

        View notificationView;
        @NotificationViewType int viewType = getNotificationViewType(statusBarNotification);
        switch (viewType) {
            case NotificationViewType.EMERGENCY_HEADSUP: {
                notificationView = mInflater.inflate(
                        R.layout.emergency_headsup_notification_template, mWrapper);
                EmergencyNotificationViewHolder holder =
                        new EmergencyNotificationViewHolder(notificationView);
                holder.bind(statusBarNotification);
                break;
            }
            case NotificationViewType.MESSAGE_HEADSUP: {
                notificationView = mInflater.inflate(
                        R.layout.message_headsup_notification_template, mWrapper);
                MessageNotificationViewHolder holder =
                        new MessageNotificationViewHolder(notificationView);
                holder.bind(
                        statusBarNotification,
                        /* isInGroup= */ false,
                        mShouldRestrictMessagePreview);
                break;
            }
            case NotificationViewType.INBOX_HEADSUP: {
                notificationView = mInflater.inflate(
                        R.layout.inbox_headsup_notification_template, mWrapper);
                InboxNotificationViewHolder holder =
                        new InboxNotificationViewHolder(notificationView);
                holder.bind(statusBarNotification, /* isInGroup= */ false);
                break;
            }
            case NotificationViewType.BASIC_HEADSUP:
            default: {
                notificationView = mInflater.inflate(
                        R.layout.basic_headsup_notification_template, mWrapper);
                BasicNotificationViewHolder holder =
                        new BasicNotificationViewHolder(notificationView);
                holder.bind(statusBarNotification, /* isInGroup= */ false);
                break;
            }
        }

        // Show animations only when a different notification comes in.
        // i.e. an update of the previous heads-up notification will not have animations.
        boolean shouldShowAnimation = !CarNotificationDiff.sameNotificationKey(
                mVisibleNotification, statusBarNotification);
        mVisibleNotification = statusBarNotification;

        // Get the height of the notification view after onLayout()
        // in order to set the height of the scrim view and do animations
        notificationView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int notificationHeight = notificationView.getHeight();
                        WindowManager.LayoutParams scrimParams =
                                (WindowManager.LayoutParams) mScrimView.getLayoutParams();
                        scrimParams.height = notificationHeight + mScrimHeightBelowNotification;
                        mWindowManager.updateViewLayout(mScrimView, scrimParams);

                        if (shouldShowAnimation) {
                            mScrimView.setY(0 - notificationHeight - mScrimHeightBelowNotification);
                            notificationView.setY(0 - notificationHeight);

                            notificationView.animate()
                                    .y(0f)
                                    .setDuration(mEnterAnimationDuration);

                            mScrimView.setVisibility(View.VISIBLE);
                            mScrimView.animate()
                                    .y(0f)
                                    .setDuration(mEnterAnimationDuration);
                        }
                        notificationView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });

        // Remove heads-up notifications after a timer
        mTimer.postDelayed(() -> clearViews(), mDuration);

        // Add swipe gesture
        notificationView.setOnTouchListener(
                new HeadsUpNotificationOnTouchListener(notificationView,
                        () -> {
                            if (CarNotificationItemTouchHelper.isCancelable(
                                    statusBarNotification.getNotification())) {
                                try {
                                    mNotificationManager.getService().cancelNotificationWithTag(
                                            statusBarNotification.getPackageName(),
                                            statusBarNotification.getTag(),
                                            statusBarNotification.getId(),
                                            mCarUserManagerHelper.getCurrentForegroundUserId());
                                } catch (RemoteException e) {
                                    throw e.rethrowFromSystemServer();
                                }
                            }
                            clearViews();
                            mTimer.removeCallbacksAndMessages(null);
                        }));
    }

    private void clearViews() {
        mVisibleNotification = null;
        mScrimView.setVisibility(View.GONE);
        mWrapper.removeAllViews();
    }

    /**
     * Choose a correct notification layout for this heads-up notification.
     * Note that the layout chosen can be different for the same notification
     * in the notification center.
     */
    @NotificationViewType
    private static int getNotificationViewType(StatusBarNotification statusBarNotification) {
        String category = statusBarNotification.getNotification().category;
        if (Notification.CATEGORY_CAR_EMERGENCY.equals(category)) {
            return NotificationViewType.EMERGENCY_HEADSUP;
        }
        if (Notification.CATEGORY_MESSAGE.equals(category)) {
            return NotificationViewType.MESSAGE_HEADSUP;
        }
        Bundle extras = statusBarNotification.getNotification().extras;
        if (extras.containsKey(Notification.EXTRA_BIG_TEXT)
                && extras.containsKey(Notification.EXTRA_SUMMARY_TEXT)) {
            return NotificationViewType.INBOX_HEADSUP;
        }
        return NotificationViewType.BASIC_HEADSUP;
    }

    /**
     * Helper method that determines whether a notification should show as a heads-up.
     *
     * <p> A notification will never be shown as a heads-up if:
     * <ul>
     * <li> Keyguard (lock screen) is showing
     * <li> Is ongoing
     * </ul>
     *
     * <p> A non-ongoing notification will be shown as a heads-up if:
     * <ul>
     * <li> Importance >= HIGH
     * <li> Category in {CAR_EMERGENCY, CAR_WARNING}
     * </ul>
     *
     * @return true if a notification should be shown as a heads-up
     */
    private boolean shouldShowHeadsUp(
            StatusBarNotification statusBarNotification,
            NotificationListenerService.RankingMap rankingMap) {

        if (mKeyguardManager.isKeyguardLocked()) {
            return false;
        }

        Notification notification = statusBarNotification.getNotification();

        // Media notification configured by OEM
        if (!mEnableMediaNotification
                && Notification.CATEGORY_TRANSPORT.equals(
                statusBarNotification.getNotification().category)) {
            return false;
        }

        // Ongoing notification configured by OEM
        if (!mEnableOngoingNotification && statusBarNotification.isOngoing()) {
            return false;
        }

        // Group alert behavior
        if (notification.suppressAlertingDueToGrouping()) {
            return false;
        }

        // Show if importance >= HIGH
        NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
        if (rankingMap.getRanking(statusBarNotification.getKey(), ranking)) {
            if (ranking.getImportance() >= NotificationManager.IMPORTANCE_HIGH) {
                return true;
            }
        }

        // Show if category in {CAR_EMERGENCY, CAR_WARNING}
        String category = notification.category;
        if (Notification.CATEGORY_CAR_EMERGENCY.equals(category)
                || Notification.CATEGORY_CAR_WARNING.equals(category)) {
            return true;
        }

        return false;
    }

    /**
     * Get CarHeadsUpNotificationManager instance.
     */
    public static CarHeadsUpNotificationManager getInstance(Context context) {
        if (sManager == null) {
            sManager = new CarHeadsUpNotificationManager(context);
        }
        return sManager;
    }

    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictions) {
        mShouldRestrictMessagePreview =
                (restrictions.getActiveRestrictions()
                        & CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE) != 0;
    }
}
