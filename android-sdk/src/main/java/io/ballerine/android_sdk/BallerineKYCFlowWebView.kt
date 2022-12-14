package io.ballerine.android_sdk

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * Custom Web view which handles the Web KYC verification flow
 *
 * @param1 - outputFileDirectory is the location of where the images are saved
 * @param2 - cameraExecutorService
 * @param3 - url is the base app url
 * @param4 - onVerificationComplete - returns the Callback function with the VerificationResult object
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BallerineKYCFlowWebView(
    outputFileDirectory: File,
    cameraExecutorService: ExecutorService,
    url: String,
    onVerificationComplete: (VerificationResult) -> Unit,
) {

    var isOpenFrontCamera by remember {
        mutableStateOf(false)
    }
    var filePathCallback: ValueCallback<Array<Uri>>? by remember {
        mutableStateOf(null)
    }
    var showMediaPicker by remember {
        mutableStateOf(false)
    }
    var isCameraPermissionAvailable by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current

    /**
     * Checks if camera permission is granted by the user
     */
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission Accepted
            isCameraPermissionAvailable = true
            showMediaPicker = true
        } else {
            // Permission Denied
            permissionRequiredToast(context = context)
        }
    }


    /**
     * Checks if camera permission is already available
     */
    when (PackageManager.PERMISSION_GRANTED) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) -> {
            isCameraPermissionAvailable = true
        }
    }

    if (showMediaPicker) {
        CameraView(
            isOpenFrontCamera = isOpenFrontCamera,
            outputDirectory = outputFileDirectory,
            executor = cameraExecutorService,
            onImageCaptured = { uri ->
                filePathCallback?.onReceiveValue(arrayOf(uri))
                filePathCallback = null
                showMediaPicker = false
            }
        ) { Log.e("CameraView", "View error:", it) }
    }

    val webViewChromeClient = object : WebChromeClient() {

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCb: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {

            if (filePathCallback != null) {
                filePathCallback!!.onReceiveValue(null)
            }
            filePathCallback = filePathCb

            //If camera permission is available open the camera preview else launch the camera permission request
            if (isCameraPermissionAvailable) {
                showMediaPicker = true
            } else {
                launcher.launch(Manifest.permission.CAMERA)
            }

            return true
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            Log.e("onConsoleMessage", " ---> ${consoleMessage?.message()}")
            return super.onConsoleMessage(consoleMessage)
        }
    }

    val webViewClient = object : WebViewClient() {

        override fun doUpdateVisitedHistory(view: WebView?, url: String, isReload: Boolean) {
            Log.d("doUpdateVisitedHistory", "url -> $url")

            // Parse url query params
            val uri = Uri.parse(url)

            uri.getQueryParameter("close").let { paramValue ->

                if (paramValue == "true") {

                    // Handle result once web KYC flow is complete

                    val isSync = uri.getBooleanQueryParameter("sync", false)
                    val status = uri.getQueryParameter("status")
                    val idvResult = uri.getQueryParameter("idvResult")
                    val code = uri.getQueryParameter("code")

                    onVerificationComplete(
                        VerificationResult(isSync, status, idvResult, code)
                    )
                }
            }

            // Sets camera to front facing while capturing selfie
            isOpenFrontCamera = url.contains("selfie") == true

            super.doUpdateVisitedHistory(view, url, isReload)
        }
    }

    /**
     * Set the Display parameters to display the view in your Android app screen
     */
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                this.webViewClient = webViewClient
                this.webChromeClient = webViewChromeClient
                this.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                }
                loadUrl(url)
            }
        })
}

// Function to generate a Toast
private fun permissionRequiredToast(context: Context) {
    Toast.makeText(
        context,
        context.getString(R.string.camera_permission_mandatory),
        Toast.LENGTH_LONG
    ).show()
}