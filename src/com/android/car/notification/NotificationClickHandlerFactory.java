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
 * limitations under the License
 */

package com.android.car.notification;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.RemoteException;
import android.service.notification.NotificationStats;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.NotificationVisibility;

/**
 * Factory that builds a {@link View.OnClickListener} to handle the logic of what to do when a
 * notification is clicked
 */
public class NotificationClickHandlerFactory {
    private static final String TAG = "NotificationClickHandlerFactory";

    private final IStatusBarService mBarService;
    private final Callback mCallback;

    public NotificationClickHandlerFactory(IStatusBarService barService,
            @Nullable Callback callback) {
        mBarService = barService;
        mCallback = callback != null ? callback : launchResult -> { };
    }

    /**
     * Returns a {@link View.OnClickListener} that should be used for the given
     * {@link StatusBarNotification}
     *
     * @param statusBarNotification that will be considered clicked when onClick is called.
     */
    public View.OnClickListener getClickHandler(StatusBarNotification statusBarNotification) {
        return v -> {
            Notification notification = statusBarNotification.getNotification();
            final PendingIntent intent = notification.contentIntent != null
                    ? notification.contentIntent
                    : notification.fullScreenIntent;
            if (intent == null) {
                return;
            }
            int result = ActivityManager.START_ABORTED;
            try {
                result = intent.sendAndReturnResult(/* context= */ null, /* code= */ 0,
                        /* intent= */ null, /* onFinished= */ null,
                        /* handler= */ null, /* requiredPermissions= */ null,
                        /* options= */ null);
            } catch (PendingIntent.CanceledException e) {
                // Do not take down the app over this
                Log.w(TAG, "Sending contentIntent failed: " + e);
            }
            NotificationVisibility notificationVisibility = NotificationVisibility.obtain(
                    statusBarNotification.getKey(),
                    /* rank= */ -1, /* count= */ -1, /* visible= */ true);
            try {
                mBarService.onNotificationClick(statusBarNotification.getKey(),
                        notificationVisibility);
                if (shouldAutoCancel(statusBarNotification)){
                    mBarService.onNotificationClear(
                            statusBarNotification.getPackageName(),
                            statusBarNotification.getTag(),
                            statusBarNotification.getId(),
                            statusBarNotification.getUser().getIdentifier(),
                            statusBarNotification.getKey(),
                            NotificationStats.DISMISSAL_SHADE,
                            NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
                            notificationVisibility);
                }
            } catch (RemoteException ex) {
                // system process is dead if we're here.
            }
            mCallback.onNotificationClicked(result);
        };

    }

    /**
     * Returns a {@link View.OnClickListener} that should be used for the
     * {@link android.app.Notification.Action} contained in the {@link StatusBarNotification}
     *
     * @param statusBarNotification that contains the clicked action.
     * @param index the index of the action clicked
     */
    public View.OnClickListener getActionClickHandler(StatusBarNotification statusBarNotification,
            int index) {
        return v -> {
            Notification notification = statusBarNotification.getNotification();
            Notification.Action[] actions = notification.actions;
            int result = ActivityManager.START_ABORTED;
            final PendingIntent intent = actions[index].actionIntent;
            NotificationVisibility notificationVisibility = NotificationVisibility.obtain(
                    statusBarNotification.getKey(),
                    /* rank= */ -1, /* count= */ -1, /* visible= */ true);
            try {
                result = intent.sendAndReturnResult(/* context= */ null, /* code= */ 0,
                        /* intent= */ null, /* onFinished= */null,
                        /* handler= */ null, /* requiredPermissions= */ null,
                        /* options= */ null);

                mBarService.onNotificationActionClick(
                        statusBarNotification.getKey(),
                        index,
                        actions[index],
                        notificationVisibility,
                        /* generatedByAssistant= */ false);
            } catch (PendingIntent.CanceledException e) {
                // Do not take down the app over this
                Log.w(TAG, "Sending contentIntent failed: " + e);
            } catch (RemoteException ex) {
                // system process is dead if we're here.
            }
            mCallback.onNotificationClicked(result);
        };
    }

    private boolean shouldAutoCancel(StatusBarNotification sbn) {
        int flags = sbn.getNotification().flags;
        if ((flags & Notification.FLAG_AUTO_CANCEL) != Notification.FLAG_AUTO_CANCEL) {
            return false;
        }
        if ((flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return false;
        }
        return true;
    }

    /**
     * Callback that will be issued after a notification is clicked
     */
    public interface Callback {

        /**
         * A notification was clicked and a PendingIntent was fired.
         *
         * @param launchResult returned from {@link PendingIntent#sendAndReturnResult}
         */
        void onNotificationClicked(int launchResult);
    }
}
