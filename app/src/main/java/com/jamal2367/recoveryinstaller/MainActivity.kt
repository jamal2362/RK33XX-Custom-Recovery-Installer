package com.jamal2367.recoveryinstaller

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.view.Window
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.recoveryinstaller.databinding.ActivityMainBinding
import com.jaredrummler.android.shell.Shell
import java.io.IOException

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private var mRecoveryFilePath: String? = null
    private var mScriptFilePath: String? = null
    private var mCurrentText: String? = null
    private val mShellMessageHandler = MessageHandler()

    // Message handler that provides a way for us to handle installation process events in the main thread
    @SuppressLint("HandlerLeak")
    private inner class MessageHandler : Handler() {
        override fun handleMessage(msg: Message) {
            // Set output text view to whatever the installation thread wants us to show..
            binding.outputTextView.text = mCurrentText
            // Check if installation thread has signalled an error..
            if (msg.what > 0) {
                val errorMessage: String = when (msg.what) {
                    1 -> {
                        "adb-ec not found on device!"
                    }
                    else -> {
                        "unknown"
                    }
                }
                // ..and if so, show an error dialog..
                val errorString = StringBuilder().append(resources.getString(R.string.error_text))
                    .append(errorMessage).append("(" + msg.what + ")")
                    .toString()
                MaterialAlertDialogBuilder(this@MainActivity).setTitle(resources.getString(R.string.error_title))
                    .setIcon(R.drawable.ic_error).setMessage(errorString).setCancelable(false).
                    // that provides the user with information and a way to recreate the application.
                setNegativeButton(android.R.string.cancel) { _, _ ->
                    this@MainActivity.recreate()
                }.create().show()
            }
        }
    }

    // Set up application
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        binding.installButton.setOnClickListener(onInstallClickListener)
        setupRawResources()
    }

    // Writes the bundled recovery and script to internal disc and saves their respective paths
    private fun setupRawResources() {
        try {
            mRecoveryFilePath = writeRawResourcesToDisc("recovery")
            mScriptFilePath = writeRawResourcesToDisc("script")
        } catch (e: IOException) {
            Log.i(
                "com.jamal2367.recoveryinstaller",
                "Error when trying to save recovery resource: " + e.message
            )
        }
    }

    // Runs the bundled script (that replaces the built-in recovery with the custom recovery)
    private val onInstallClickListener = View.OnClickListener {
        Thread {
            Log.i("com.jamal2367.recoveryinstaller", "Starting installation of custom recovery..")
            mCurrentText = getString(R.string.wait_text)
            // Update GUI with wait text..
            mShellMessageHandler.sendEmptyMessage(-127)
            // Run shell script
            val commandResult = Shell.SH.run(mScriptFilePath)
            // Get shell command result
            mCurrentText = commandResult.getStdout()
            // Update GUI with command result
            mShellMessageHandler.sendEmptyMessage(commandResult.exitCode)
            Log.i("com.jamal2367.recoveryinstaller", "Installing custom recovery finished..")
        }.start()
    }

    // Writes the given raw resource to the internal disc and returns the path to the resource on disc
    @SuppressLint("SetWorldWritable", "SetWorldReadable")
    @Throws(IOException::class)
    private fun writeRawResourcesToDisc(rawResourceName: String): String {
        Log.i(
            "com.jamal2367.recoveryinstaller",
            "Trying to write resource with name: $rawResourceName to disc."
        )
        val rawResource =
            resources.openRawResource(resources.getIdentifier(rawResourceName, "raw", packageName))
        val rawResourceBytes = ByteArray(rawResource.available())
        rawResource.read(rawResourceBytes)
        rawResource.close()
        val openFileOutput = openFileOutput(rawResourceName, 0)
        openFileOutput.write(rawResourceBytes)
        openFileOutput.close()
        val fileStreamPath = getFileStreamPath(rawResourceName)
        fileStreamPath.setWritable(true, false)
        fileStreamPath.setReadable(true, false)
        fileStreamPath.setExecutable(true, false)
        val retPath = fileStreamPath.path
        Log.i("com.jamal2367.recoveryinstaller", "Resource $rawResourceName saved at: $retPath")
        return retPath
    }

}
