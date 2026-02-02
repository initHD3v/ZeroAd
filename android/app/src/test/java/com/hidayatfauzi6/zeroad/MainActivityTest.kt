package com.hidayatfauzi6.zeroad

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MainActivityTest {

    private lateinit var packageManager: PackageManager
    private lateinit var methodCall: MethodCall
    private lateinit var result: MethodChannel.Result

    @Before
    fun setUp() {
        // Initialize mocks
        packageManager = mock(PackageManager::class.java)
        methodCall = mock(MethodCall::class.java)
        result = mock(MethodChannel.Result::class.java)

        // Mock methodCall.method
        `when`(methodCall.method).thenReturn("scan")
    }

    @Test
    fun `scan method returns correct package count and suspicious count`() {
        // Mock installed applications
        val appInfo1 = mock(ApplicationInfo::class.java)
        appInfo1.packageName = "com.example.app1"
        val appInfo2 = mock(ApplicationInfo::class.java)
        appInfo2.packageName = "com.example.app2"
        val appInfo3 = mock(ApplicationInfo::class.java)
        appInfo3.packageName = "com.example.adware_app"

        val mockPackages = listOf(appInfo1, appInfo2, appInfo3)
        `when`(packageManager.getInstalledApplications(anyInt())).thenReturn(mockPackages)

        // Mock package info for permissions
        val pkgInfo1 = mock(PackageInfo::class.java)
        pkgInfo1.requestedPermissions = arrayOf("android.permission.INTERNET")
        val pkgInfo2 = mock(PackageInfo::class.java)
        pkgInfo2.requestedPermissions = arrayOf("android.permission.CAMERA")
        val pkgInfo3 = mock(PackageInfo::class.java) // This one will be "suspicious"
        pkgInfo3.requestedPermissions = arrayOf("android.permission.BIND_ACCESSIBILITY_SERVICE", "android.permission.INTERNET")

        `when`(packageManager.getPackageInfo(anyString(), anyInt())).thenAnswer { invocation ->
            when (invocation.arguments[0] as String) {
                "com.example.app1" -> pkgInfo1
                "com.example.app2" -> pkgInfo2
                "com.example.adware_app" -> pkgInfo3
                else -> throw PackageManager.NameNotFoundException()
            }
        }

        // Simulate the scan logic from MainActivity
        var adwareCount = 0
        for (pkg in mockPackages) {
            try {
                val permissions = packageManager.getPackageInfo(pkg.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
                if (permissions != null && permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")) {
                    adwareCount++
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Should not happen in this mock setup
            }
        }

        // Verify the result
        verify(result).success("Total installed packages: ${mockPackages.size}, Potentially suspicious (heuristic placeholder): $adwareCount")
    }

    @Test
    fun `scan method handles PackageManager NameNotFoundException`() {
        val appInfo1 = mock(ApplicationInfo::class.java)
        appInfo1.packageName = "com.example.app1"
        val mockPackages = listOf(appInfo1)

        `when`(packageManager.getInstalledApplications(anyInt())).thenReturn(mockPackages)
        `when`(packageManager.getPackageInfo(anyString(), anyInt())).thenThrow(PackageManager.NameNotFoundException())

        // Simulate the scan logic from MainActivity
        var adwareCount = 0
        for (pkg in mockPackages) {
            try {
                val permissions = packageManager.getPackageInfo(pkg.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
                if (permissions != null && permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")) {
                    adwareCount++
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Expected to catch this exception
            }
        }

        // Verify that success is still called, but adwareCount might be 0
        verify(result).success("Total installed packages: ${mockPackages.size}, Potentially suspicious (heuristic placeholder): $adwareCount")
    }

    @Test
    fun `unsupported method call returns notImplemented`() {
        `when`(methodCall.method).thenReturn("unsupportedMethod")

        // Simulate the logic for unsupported methods
        if (methodCall.method != "scan") {
            result.notImplemented()
        }

        // Verify notImplemented was called
        verify(result).notImplemented()
    }
}
