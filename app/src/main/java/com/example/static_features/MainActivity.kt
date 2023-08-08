package com.example.static_features

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import okhttp3.Callback
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var responseTextView: TextView
    private lateinit var apkSpinner: Spinner
    private lateinit var selectButton: Button
    private lateinit var scanHistoryRecyclerView: RecyclerView
    private lateinit var scanHistoryAdapter: ScanHistoryAdapter
    private lateinit var clearHistoryButton: Button

    private lateinit var installedApps: List<ApplicationInfo>
    private val scanHistoryList = mutableListOf<ScanHistoryItem>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        responseTextView = findViewById(R.id.responseTextView)
        scanHistoryRecyclerView = findViewById(R.id.scanHistoryRecyclerView)

        apkSpinner = findViewById(R.id.apkSpinner)
        selectButton = findViewById(R.id.selectButton)
        clearHistoryButton = findViewById(R.id.clearHistoryButton)

        selectButton.setOnClickListener { displaySelectedAppFeatures() }
        clearHistoryButton.setOnClickListener { clearScanHistory() }

        retrieveInstalledApps()
        populateSpinner()

        // Setup RecyclerView and adapter
        scanHistoryAdapter = ScanHistoryAdapter(scanHistoryList)
        scanHistoryRecyclerView.layoutManager = LinearLayoutManager(this)
        scanHistoryRecyclerView.adapter = scanHistoryAdapter

        displaySelectedAppFeatures()
    }

    private fun retrieveInstalledApps() {
        val packageManager: PackageManager = packageManager
        installedApps = packageManager.getInstalledApplications(
            PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
        )
    }

    private fun populateSpinner() {
        val apkNames = installedApps.map { it.loadLabel(packageManager).toString() }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            apkNames
        )
        apkSpinner.adapter = adapter
    }

    private fun displaySelectedAppFeatures() {
        val selectedApkName = apkSpinner.selectedItem.toString()
        val selectedAppInfo = installedApps.find { it.loadLabel(packageManager).toString() == selectedApkName }

        selectedAppInfo?.let {
            val packageName = selectedAppInfo.packageName
            val appName = selectedAppInfo.loadLabel(packageManager).toString()
            val appInfo: ApplicationInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )

            val statsData = LinkedHashMap<String, String>()
            // Extract features for other packages
            statsData["APICall"] = getApiCall()
            statsData["Permission"] = getPermission()
            statsData["URL"] = getUrl()
            statsData["Provider"] = getProvider()
            statsData["Feature"] = getFeature()
            statsData["Intent"] = "NULL"
            statsData["Activity"] = getActivity()
            statsData["Call"] = getCall()
            statsData["ServiceReceiver"] = getServiceReceiver()
            statsData["RealPermission"] = getRealPermission()

            val gson = Gson()
            val json = gson.toJson(statsData)

            val requestBody = FormBody.Builder()
                .add("stringToAppend", json)
                .build()

            val client = OkHttpClient()
            val request = Request.Builder()
                .url("http://192.168.167.165:8000")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Request failed: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            val responseData = response.body?.string()
                            responseTextView.text = responseData
                            responseTextView.visibility = View.VISIBLE
                            Toast.makeText(
                                applicationContext,
                                "Data appended successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            try {
                                // Parse the JSON response
                                val jsonResponse = JSONObject(responseData)
                                val label = jsonResponse.getString("Label")
                                val confidenceLevel = jsonResponse.getInt("Confidence level")

                                // Create separate strings for label and confidence level
                                val labelStatement = "Predicted label: $label"
                                val confidenceStatement = "Confidence level: $confidenceLevel"

                                // Display the label and confidence level as separate lines
                                responseTextView.text = labelStatement + "\n" + confidenceStatement

                                // Update the score bar
                                val scoreProgressBar: ProgressBar = findViewById(R.id.scoreProgressBar)
                                scoreProgressBar.max = 100 // Set the maximum value of the progress bar

                                // Calculate the progress bar's length based on the confidence level
                                val progressLength = (confidenceLevel * scoreProgressBar.max) / 100
                                scoreProgressBar.progress = progressLength

                                // Set color based on confidence level
                                val color = if (confidenceLevel > 50) {
                                    Color.GREEN
                                } else {
                                    Color.RED
                                }
                                scoreProgressBar.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)

                                // Add the scan result to the scan history list
                                val scanHistoryItem = ScanHistoryItem(selectedApkName, label, confidenceLevel)
                                scanHistoryList.add(scanHistoryItem)

                                // Notify the adapter that data has changed
                                scanHistoryAdapter.notifyItemInserted(scanHistoryList.size - 1)

                                // Scroll to the bottom of the RecyclerView
                                scanHistoryRecyclerView.scrollToPosition(scanHistoryList.size - 1)

                            } catch (e: JSONException) {
                                Toast.makeText(
                                    applicationContext,
                                    "Failed to parse server response",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                applicationContext,
                                "Failed to append data: ${response.code}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            })
        }
    }




    private fun getPermission(): String {
        // Implement your logic to get the permission information
        // For example, you can retrieve the list of permissions from the app's manifest file
        val selectedApkName = apkSpinner.selectedItem.toString()
        val selectedAppInfo =
            installedApps.find { it.loadLabel(packageManager).toString() == selectedApkName }

        selectedAppInfo?.let {
            val packageName = selectedAppInfo.packageName
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS
            )
            val permissions = packageInfo.requestedPermissions
            return permissions?.joinToString("\n") ?: "No permissions found"
        }
        return "Permission information not available"
    }

    private fun getUrl(): String {
        // Implement your logic to get the URL information
        // For example, you can retrieve the URL from the app's metadata
        val selectedApkName = apkSpinner.selectedItem.toString()
        val selectedAppInfo =
            installedApps.find { it.loadLabel(packageManager).toString() == selectedApkName }

        selectedAppInfo?.let {
            val packageName = selectedAppInfo.packageName
            val appInfo =
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val url = appInfo.metaData?.getString("url")
            return url ?: "URL not found"
        }
        return "URL information not available"
    }

    private fun getProvider(): String {
        // Implement your logic to get the provider information
        // For example, you can retrieve the list of content providers from the app's manifest file
        val selectedApkName = apkSpinner.selectedItem.toString()
        val selectedAppInfo =
            installedApps.find { it.loadLabel(packageManager).toString() == selectedApkName }

        selectedAppInfo?.let {
            val packageName = selectedAppInfo.packageName
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_PROVIDERS)
            val providers = packageInfo.providers
            return providers?.joinToString("\n") ?: "No providers found"
        }
        return "Provider information not available"
    }

    private fun getFeature(): String {
        // Implement your logic to get the feature information
        // For example, you can retrieve the list of features from the app's manifest file
        val selectedApkName = apkSpinner.selectedItem.toString()
        val selectedAppInfo =
            installedApps.find { it.loadLabel(packageManager).toString() == selectedApkName }

        selectedAppInfo?.let {
            val packageName = selectedAppInfo.packageName
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_CONFIGURATIONS)
            val features = packageInfo.reqFeatures
            return features?.joinToString("\n") ?: "No features found"
        }
        return "Feature information not available"
    }

    private fun getActivity(): String {
        val selectedApkName = apkSpinner.selectedItem.toString()
        val selectedAppInfo =
            installedApps.find { it.loadLabel(packageManager).toString() == selectedApkName }

        selectedAppInfo?.let {
            val packageName = selectedAppInfo.packageName
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val activities = packageInfo.activities
            return activities?.joinToString("\n") ?: "No activities found"
        }
        return "Activity information not available"
    }

    private fun getCall(): String {
        val selectedApkName = apkSpinner.selectedItem.toString()
        val selectedAppInfo =
            installedApps.find { it.loadLabel(packageManager).toString() == selectedApkName }

        selectedAppInfo?.let {
            val packageName = selectedAppInfo.packageName
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val permissions = packageInfo.requestedPermissions
            val callPermissions =
                permissions?.filter { it.startsWith("android.permission.CALL") }
            return callPermissions?.joinToString("\n") ?: "No call permissions found"
        }
        return "Call information not available"
    }

    private fun getApiCall(): String {
        val apiCalls = listOf(
            "GET /api/users",
            "POST /api/login",
            "PUT /api/users/1",
            "DELETE /api/users/1"
        )

        return apiCalls.joinToString("\n")
    }

    private fun getServiceReceiver(): String {
        // Implement your logic to get the service receiver information
        // For example, you can retrieve the list of service receivers from the app's manifest file
        val selectedApkName = apkSpinner.selectedItem.toString()
        val selectedAppInfo =
            installedApps.find { it.loadLabel(packageManager).toString() == selectedApkName }

        selectedAppInfo?.let {
            val packageName = selectedAppInfo.packageName
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_RECEIVERS)
            val receivers = packageInfo.receivers
            return receivers?.joinToString("\n") ?: "No service receivers found"
        }
        return "Service receiver information not available"
    }

    private fun getRealPermission(): String {
        return "Real permission information"
    }


    private fun clearScanHistory() {
        scanHistoryList.clear()
        scanHistoryAdapter.notifyDataSetChanged()
    }

    inner class ScanHistoryAdapter(private val scanHistoryList: List<ScanHistoryItem>) :
        RecyclerView.Adapter<ScanHistoryAdapter.ScanHistoryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanHistoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.scan_history_item, parent, false)
            return ScanHistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: ScanHistoryViewHolder, position: Int) {
            val scanHistoryItem = scanHistoryList[position]
            holder.bind(scanHistoryItem)
        }

        override fun getItemCount(): Int {
            return scanHistoryList.size
        }

        inner class ScanHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val apkNameTextView: TextView = itemView.findViewById(R.id.apkNameTextView)
            private val labelTextView: TextView = itemView.findViewById(R.id.labelTextView)
            private val confidenceTextView: TextView = itemView.findViewById(R.id.confidenceTextView)

            fun bind(scanHistoryItem: ScanHistoryItem) {
                apkNameTextView.text = scanHistoryItem.apkName
                labelTextView.text = scanHistoryItem.label
                confidenceTextView.text = scanHistoryItem.confidenceLevel.toString()
            }
        }
    }

    data class ScanHistoryItem(val apkName: String, val label: String, val confidenceLevel: Int)
}