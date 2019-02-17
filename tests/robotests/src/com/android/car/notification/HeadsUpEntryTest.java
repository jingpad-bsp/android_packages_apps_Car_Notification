/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.content.Context;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarNotificationRobolectricTestRunner.class)
public class HeadsUpEntryTest {

    private Context mContext;

    private static final String PKG_1 = "package_1";
    private static final String PKG_2 = "package_2";
    private static final String OP_PKG = "OpPackage";
    private static final int ID = 1;
    private static final String TAG = "Tag";
    private static final int UID = 2;
    private static final int INITIAL_PID = 3;
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String CONTENT_TITLE = "CONTENT_TITLE";
    private static final String OVERRIDE_GROUP_KEY = "OVERRIDE_GROUP_KEY";
    private static final String OVERRIDE_GROUP_KEY_123 = "OVERRIDE_GROUP_KEY_123";
    private static final long POST_TIME = 12345l;
    private static final UserHandle USER_HANDLE = new UserHandle(12);

    private Notification.Builder mNotificationBuilder1;
    private StatusBarNotification mNotification1;
    private HeadsUpEntry mHeadsUpEntry;

    @Before
    public void setupBaseActivityAndLayout() {
        mContext = RuntimeEnvironment.application;
        mNotificationBuilder1 = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        mNotification1 = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder1.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        mHeadsUpEntry = new HeadsUpEntry(mNotification1, mContext);
    }

    @Test
    public void headsUpEntry_shouldInitializePostTime() {
        long currentTme = System.currentTimeMillis();
        mHeadsUpEntry = new HeadsUpEntry(mNotification1, mContext);

        assertThat(mHeadsUpEntry.getPostTime()).isNotEqualTo(0);
        assertThat(currentTme).isAtMost(mHeadsUpEntry.getPostTime());
    }

    @Test
    public void updatePostTime_shouldUpdatePostTime() {
        long currentPostTime = mHeadsUpEntry.getPostTime();
        mHeadsUpEntry.updatePostTime();

        assertThat(currentPostTime).isAtMost(mHeadsUpEntry.getPostTime());
    }

    @Test
    public void headsUpEntry_shouldSetStatusBarNotification() {
        assertThat(mNotification1).isEqualTo(mHeadsUpEntry.getStatusBarNotification());
    }

    @Test
    public void headsUpEntry_shouldInitializeHandler() {
        assertThat(mHeadsUpEntry.getHandler()).isNotNull();
    }

    @Test
    public void setFrameLayout_shouldSetFrameLayout() {
        mHeadsUpEntry = new HeadsUpEntry(mNotification1, mContext);

        assertThat(mHeadsUpEntry.getFrameLayout()).isNull();

        mHeadsUpEntry.setFrameLayout(new FrameLayout(mContext));

        assertThat(mHeadsUpEntry.getFrameLayout()).isNotNull();
    }

    @Test
    public void setNotificationView_shouldSetNotificationView() {
        mHeadsUpEntry = new HeadsUpEntry(mNotification1, mContext);

        assertThat(mHeadsUpEntry.getNotificationView()).isNull();

        mHeadsUpEntry.setNotificationView(new FrameLayout(mContext));

        assertThat(mHeadsUpEntry.getNotificationView()).isNotNull();
    }

    @Test
    public void getScrimView_shouldNoBeNull() {
        mHeadsUpEntry = new HeadsUpEntry(mNotification1, mContext);

        assertThat(mHeadsUpEntry.getScrimView()).isNotNull();
    }
}
