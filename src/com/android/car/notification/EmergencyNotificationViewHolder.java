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

import android.annotation.Nullable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.car.widget.ColumnCardView;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Notification view template that displays a car emergency notification.
 */
public class EmergencyNotificationViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "car_emergency";
    private final Context mContext;
    private final CarNotificationHeaderView mHeaderView;
    private final CarNotificationActionsView mActionsView;
    private final TextView mTitleTextView;
    private final TextView mContentTextView;
    private final View mParentView;
    private final FrameLayout mBigContentView;
    private final int mEmergencyActionBarColor;

    public EmergencyNotificationViewHolder(View view) {
        super(view);
        mContext = view.getContext();
        mParentView = view;
        mHeaderView = view.findViewById(R.id.notification_header);
        mActionsView = view.findViewById(R.id.notification_actions);
        mTitleTextView = view.findViewById(R.id.notification_title);
        mContentTextView = view.findViewById(R.id.notification_text);
        mBigContentView = view.findViewById(R.id.big_content_view);
        mEmergencyActionBarColor = mContext.getColor(R.color.emergency_action_bar_background_color);
    }

    /**
     * Binds a {@link StatusBarNotification} to a car emergency notification template.
     *
     * @param statusBarNotification passing {@code null} clears the view.
     */
    public void bind(StatusBarNotification statusBarNotification) {
        reset();
        if (statusBarNotification == null) {
            return;
        }

        Notification notification = statusBarNotification.getNotification();

        if (notification.contentIntent != null) {
            mParentView.setOnClickListener(v -> {
                try {
                    notification.contentIntent.send();
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Cannot send pendingIntent in action button");
                }
            });
        }

        if (notification.bigContentView != null) {
            View view = notification.bigContentView.apply(mContext, /* parent= */ mBigContentView);
            mBigContentView.setVisibility(View.VISIBLE);
            mBigContentView.addView(view);
            // If a notification came with a custom content view,
            // do not bind anything else other than the custom view.
            return;
        }

        mHeaderView.bind(statusBarNotification);
        mActionsView.bind(statusBarNotification, /* isInGroup= */ false);

        // Override the car action bar color that was set in CarNotificationActionsView.bind()
        mActionsView.setBackgroundColor(mEmergencyActionBarColor);

        Bundle extraData = notification.extras;
        CharSequence title = extraData.getCharSequence(Notification.EXTRA_TITLE);
        if (!TextUtils.isEmpty(title)) {
            mTitleTextView.setVisibility(View.VISIBLE);
            mTitleTextView.setText(title);
        }

        CharSequence text = extraData.getCharSequence(Notification.EXTRA_TEXT);
        if (!TextUtils.isEmpty(text)) {
            mContentTextView.setVisibility(View.VISIBLE);
            mContentTextView.setText(text);
        }
    }

    /**
     * Resets the basic notification view empty for recycling.
     */
    private void reset() {
        mParentView.setClickable(false);
        mParentView.setOnClickListener(null);

        mBigContentView.removeAllViews();
        mBigContentView.setVisibility(View.GONE);

        mTitleTextView.setText(null);
        mTitleTextView.setVisibility(View.GONE);

        mContentTextView.setText(null);
        mContentTextView.setVisibility(View.GONE);

        mHeaderView.reset();

        mActionsView.reset();
    }
}
