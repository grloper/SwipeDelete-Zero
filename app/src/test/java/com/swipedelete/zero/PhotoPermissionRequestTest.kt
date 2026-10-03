package com.swipedelete.zero

import com.swipedelete.zero.ui.screens.dashboard.photoReviewPermissions
import org.junit.Assert.*
import org.junit.Test

class PhotoPermissionRequestTest {
    @Test fun `modern onboarding never requests video or audio`() {
        for (sdk in listOf(33, 34, 35, 36)) {
            val permissions = photoReviewPermissions(sdk).toList()
            assertTrue(permissions.contains("android.permission.READ_MEDIA_IMAGES"))
            assertFalse(permissions.contains("android.permission.READ_MEDIA_VIDEO"))
            assertFalse(permissions.contains("android.permission.READ_MEDIA_AUDIO"))
            assertEquals(sdk >= 34, permissions.contains("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
        }
    }
    @Test fun `legacy access uses supported storage permission`() {
        assertArrayEquals(arrayOf("android.permission.READ_EXTERNAL_STORAGE"), photoReviewPermissions(32))
    }
}
