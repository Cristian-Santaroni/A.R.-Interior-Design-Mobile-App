package com.example.camera

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.node.CloudAnchorNode
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.gesture.MoveGestureDetector
import io.github.sceneview.gesture.RotateGestureDetector
import io.github.sceneview.gesture.ScaleGestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

private var kmodel="https://firebasestorage.googleapis.com/v0/b/mac-proj-5f6eb.appspot.com/o/black_sofa.glb?alt=media&token=fbb4bcd3-388a-42bf-b328-4f2911aac288"
class ARSessionActivity: AppCompatActivity(R.layout.ar_activity) {

    lateinit var b : ImageButton
    lateinit var b1 : Button
    lateinit var sceneView: ARSceneView
    lateinit var loadingView: View
    lateinit var instructionText: TextView
    lateinit var horiz_hide_show: LinearLayout
    lateinit var button_hide_show : Button
    private lateinit var lastCloudAnchorNode: Anchor
    private var currentScaleFactor = 0.7f
    var isRotating = false
    private lateinit var databaseReference: DatabaseReference
    var old_anchor : Anchor? = null


    private val anchorsList = mutableListOf<Triple<AnchorNode?, String, Float3>>()

    var vis: Boolean = false
    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }

    var anchorNode: AnchorNode? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    var trackingFailureReason: TrackingFailureReason? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    fun updateInstructions() {
        val frame = sceneView.frame
        instructionText.text = (trackingFailureReason?.let {
            it.getDescription(this)
        } ?: if (anchorNode == null) {
            getString(R.string.tap_anywhere_to_add_model)
        } else {
            null
        })

    }

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFullScreen(
            findViewById(R.id.rootView),
            fullScreen = true,
            hideSystemBars = false,
            fitsSystemWindows = false
        )
        val sessionId = intent.getStringExtra("sessionId")

        database = FirebaseDatabase.getInstance().reference
        databaseReference = FirebaseDatabase.getInstance().getReference("sessions/$sessionId/models")


        instructionText = findViewById(R.id.instructionText)
        loadingView = findViewById(R.id.loadingView)
        sceneView = findViewById<ARSceneView?>(R.id.sceneView).apply {
            planeRenderer.isEnabled = true
            planeRenderer.isShadowReceiver=true
            configureSession { session, config ->
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.cloudAnchorMode= Config.CloudAnchorMode.ENABLED
                config.geospatialMode = Config.GeospatialMode.DISABLED
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                session.configure(config)
            }

            onSessionUpdated = { _, frame ->
                frame.getUpdatedTrackables(Plane::class.java).firstOrNull()?.let { _ ->
                    if (frame != null) {
                        val quality = sceneView.session?.estimateFeatureMapQualityForHosting(frame.camera.pose)
                        instructionText.text = when (quality) {
                            Session.FeatureMapQuality.INSUFFICIENT -> "Insufficient visual data - move the camera before host"
                            Session.FeatureMapQuality.SUFFICIENT -> "Sufficient visual data. Continue moving the device around the object to get better results"
                            Session.FeatureMapQuality.GOOD -> "Good visual data!"
                            else -> instructionText.text // Keep the current instruction if quality is unknown
                        }
                    }
                }
            }



            onTrackingFailureChanged = { reason ->
                this@ARSessionActivity.trackingFailureReason = reason
            }

            onGestureListener = object : GestureDetector.OnGestureListener {
                override fun onDown(e: MotionEvent, node: Node?) {

                }

                override fun onShowPress(e: MotionEvent, node: Node?) {


                }

                override fun onSingleTapUp(e: MotionEvent, node: Node?) {

                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    node: Node?,
                    distance: Float2
                ) {
                }

                override fun onLongPress(e: MotionEvent, node: Node?) {

                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    node: Node?,
                    velocity: Float2
                ) {
                }

                override fun onSingleTapConfirmed(e: MotionEvent, node: Node?) {
                    if (node == null) {
                        instructionText.text = "Updating session. . ."
                        val hitResultList = frame?.hitTest(e.x, e.y)
                        hitResultList?.firstOrNull { hitResult ->
                            hitResult.trackable is Plane && (hitResult.trackable as Plane).isPoseInPolygon(hitResult.hitPose) && (hitResult.trackable as Plane).type == Plane.Type.HORIZONTAL_UPWARD_FACING
                        }?.let { hitResult ->
                            // Create an anchor at the hit test point on the detected plane
                            val anchor = hitResult.createAnchor()
                            addAnchorNode(anchor, Float3(0.37438163f, 0.37438163f, 0.37438163f))
                            addModelToFirebase(kmodel,anchor)
                        }


                    }
                }

                override fun onDoubleTap(e: MotionEvent, node: Node?) {

                }

                override fun onDoubleTapEvent(e: MotionEvent, node: Node?) {
                    if(node!=null)
                    {
                        instructionText.text = "Updating session. . ."

                        Log.d("Pose", "Product pose: MI HAI PRESO")
                        Log.d("Node", "Node= $node")
                        val dad: AnchorNode = node.parent as AnchorNode
                        val dadanchor : Anchor = dad.anchor
                        Log.d("DAD", "DAD= $dad")
                        Log.d("DAD", "DAD ANCHOR= $dadanchor")

                        anchorsList.removeIf { (anchor, _, _) ->
                            anchor.toString() == dad.toString()

                        }
                        node.parent=null
                        node.destroy()
                        deleteModelFromFirebase(node,dadanchor)

                    }
                }

                override fun onContextClick(e: MotionEvent, node: Node?) {

                }

                override fun onMoveBegin(
                    detector: MoveGestureDetector,
                    e: MotionEvent,
                    node: Node?
                ) {
                    instructionText.text = "Updating session. . ."

                    if (node != null) {
                        if (node.parent is AnchorNode){
                            Log.d("ANCHOR NODE OLD (in teoria)", "${anchorNode?.anchor}")
                            old_anchor = (node.parent as AnchorNode).anchor
                        }
                    }
                }

                override fun onMove(detector: MoveGestureDetector, e: MotionEvent, node: Node?) {
                    if (node != null) {
                        val modelnode : ModelNode = node as ModelNode

                        modelnode.scale

                        Log.d("SCALE","Scale to units: ${modelnode?.scale}")

                    }
                }

                @SuppressLint("SuspiciousIndentation")
                override fun onMoveEnd(detector: MoveGestureDetector, e: MotionEvent, node: Node?) {
                    if (node != null) {
                        val modelnode : ModelNode = node as ModelNode

                        modelnode.scale

                        Log.d("SCALE","Scale to units: ${modelnode?.scale}")

                        if (node.parent is AnchorNode){
                            Log.d("ANCHOR NODE NEW (in teoria)", "${anchorNode?.anchor}")
                            Log.d("ANCHOR NODE NEW (in teoria)", "new: ${anchorNode?.anchor?.pose}")
                            for ((anchornode, model, scaling) in anchorsList){
                                if (anchornode.toString() == node.parent.toString() && (node.parent as AnchorNode).anchor != old_anchor){
                                    // Update the entry with the new AnchorNode
                                    anchorsList.remove(Triple(anchornode, model, scaling))
                                    anchorsList.add(Triple(node.parent as AnchorNode, model, scaling))
                                    val session = sceneView.session
                                    val frame = sceneView.frame
                                    val newAnchor = (node.parent as AnchorNode).anchor

                                    // Check if both old and new anchors are available
                                    if (old_anchor != null && newAnchor != null) {
                                        if (frame != null && session != null) {
                                            // Host the new cloud anchor
                                            (node.parent as AnchorNode).let { anchorNode ->
                                                sceneView.addChildNode(CloudAnchorNode(sceneView.engine, anchorNode!!.anchor).apply {
                                                    isScaleEditable = false

                                                    isRotationEditable = false
                                                    if (session != null) {
                                                        host(session) { cloudAnchorId, state ->
                                                            Log.d(
                                                                "CloudAnchor",
                                                                "STATE: $state, CloudAnchorId: $cloudAnchorId"
                                                            )
                                                            when (state) {
                                                                Anchor.CloudAnchorState.SUCCESS -> {
                                                                    Log.d(
                                                                        "CloudAnchor",
                                                                        "Cloud anchor hosted successfully: $cloudAnchorId"
                                                                    )

                                                                    if (cloudAnchorId != null) {
                                                                        updateFirebaseAnchor(old_anchor!!, newAnchor, cloudAnchorId)

                                                                    }


                                                                }

                                                                else -> {}
                                                            }
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                    }
                                    break
                                }
                            }

                        }

                    }
                }

                override fun onRotateBegin(detector: RotateGestureDetector, e: MotionEvent, node: Node?) {
                }

                override fun onRotate(
                    detector: RotateGestureDetector,
                    e: MotionEvent,
                    node: Node?
                ) {
                }

                override fun onRotateEnd(detector: RotateGestureDetector, e: MotionEvent, node: Node?) {
                }



                override fun onScaleBegin(
                    detector: ScaleGestureDetector,
                    e: MotionEvent,
                    node: Node?
                ) {

                }

                override fun onScale(detector: ScaleGestureDetector, e: MotionEvent, node: Node?) {
                    if (node is ModelNode) {
                        scaleModelNode(node, detector)
                    } else if (node is AnchorNode) {
                        // Check if one of the children is a ModelNode
                        val modelNodeChild = node.childNodes.firstOrNull { it is ModelNode } as? ModelNode
                        modelNodeChild?.let { scaleModelNode(it, detector) }
                    }
                }

                override fun onScaleEnd(detector: ScaleGestureDetector, e: MotionEvent, node: Node?) {
                    if (node is ModelNode) {
                        scaleModelNode(node, detector)

                        if (node.parent is AnchorNode) {
                            updateAnchorList(node.parent as AnchorNode, node)
                        }
                    } else if (node is AnchorNode) {
                        // Check if one of the children is a ModelNode
                        val modelNodeChild = node.childNodes.firstOrNull { it is ModelNode } as? ModelNode
                        modelNodeChild?.let {
                            scaleModelNode(it, detector)
                            updateAnchorList(node, it)
                        }
                    }
                }

                private fun scaleModelNode(modelNode: ModelNode, detector: ScaleGestureDetector) {
                    val scaleFactor = detector.scaleFactor

                    // Adjust the scale based on the scaleFactor
                    val newScaleX = modelNode.scale.x * scaleFactor
                    val newScaleY = modelNode.scale.y * scaleFactor
                    val newScaleZ = modelNode.scale.z * scaleFactor

                    // Define your scale limits
                    val minScale = 0.37438163f
                    val maxScale = 1.5f

                    // Clamp the new scale values to stay within the limits
                    val clampedScaleX = newScaleX.coerceIn(minScale, maxScale)
                    val clampedScaleY = newScaleY.coerceIn(minScale, maxScale)
                    val clampedScaleZ = newScaleZ.coerceIn(minScale, maxScale)

                    // Set the clamped scale to the modelNode
                    modelNode.scale = Float3(
                        clampedScaleX.toFloat(),
                        clampedScaleY.toFloat(),
                        clampedScaleZ.toFloat()
                    )
                }

                private fun updateAnchorList(anchorNode: AnchorNode, modelNode: ModelNode) {
                    Log.d("SCALE", "Scale to units: ${modelNode?.scale}")

                    for ((anchornode, model, scaling) in anchorsList) {
                        if (anchornode.toString() == anchorNode.toString()) {
                            // Update the entry with the new AnchorNode
                            anchorsList.remove(Triple(anchornode, model, scaling))
                            anchorsList.add(Triple(anchornode, model, modelNode?.scale) as Triple<AnchorNode?, String, Float3>)
                            break // Exit the loop once the replacement is done
                        }
                    }
                }
            }
        }

        // Check if anchor ID is passed in the intent
        val projectTitle = intent.getStringExtra("projectTitle")
        val anchorIdList = intent.getSerializableExtra("anchor_id_list") as? ArrayList<HashMap<String, String>>

        if (!anchorIdList.isNullOrEmpty()) {
            // Anchor ID list is present, iterate over the list and resolve each anchor
            b1 = findViewById<Button?>(R.id.hostButton).apply {
                text = "LOAD PROJECT"
                setOnClickListener {
                    val session = sceneView.session ?: return@setOnClickListener

                    for (anchorData in anchorIdList) {
                        instructionText.text = "Resolving. . ."
                        val anchorId = anchorData["anchor_id"]
                        kmodel = anchorData["model"].toString()
                        val scaling = anchorData["scaling"].toString()
                        Log.d("SCALE","RESOLVED SCALE STRING $scaling")
                        if (!anchorId.isNullOrBlank()) {
                            // Resolve the anchor using the anchorId
                            val resolvedAnchor = session.resolveCloudAnchor(anchorId)
                            if (resolvedAnchor != null) {
                                val resolvedpose = resolvedAnchor.pose
                                Log.d("POSE RESOLVED","Resolved pose = $resolvedpose")
                                // Anchor resolved successfully, add anchor node
                                val float3Object = scaling?.let { it1 -> parseFloat3FromString(it1) }
                                addAnchorNode(resolvedAnchor, float3Object)
                                Log.d("Resolve", "Resolved $anchorId $kmodel $float3Object")
                            } else {
                                // Handle anchor resolution failure
                                val resolutionFailureToast = Toast.makeText(
                                    context,
                                    "Failed to resolve anchor: $anchorId",
                                    Toast.LENGTH_LONG
                                )
                                resolutionFailureToast.show()
                                Log.d("CloudAnchor", "Failed to resolve anchor: $anchorId")
                            }
                        }
                    }
                    instructionText.text = ""
                }
            }
        } else {
            // No anchor ID passed, proceed with hosting logic
            b1 = findViewById<Button?>(R.id.hostButton).apply {
                isClickable = false
                isEnabled = false
                isVisible = false
                setOnClickListener {

                    // Disable the button during the onClickListener execution
                    isClickable = false
                    isEnabled = false

                    val session = sceneView.session ?: return@setOnClickListener
                    val frame = sceneView.frame ?: return@setOnClickListener

                    if (sceneView.session?.estimateFeatureMapQualityForHosting(frame.camera.pose) == Session.FeatureMapQuality.INSUFFICIENT) {
                        val insufficientVisualDataToast = Toast.makeText(
                            context,
                            R.string.insufficient_visual_data,
                            Toast.LENGTH_LONG
                        )
                        insufficientVisualDataToast.show()
                        Log.d("CloudAnchor", "Insufficient visual data for hosting")
                        // Enable the button after showing the toast
                        isClickable = true
                        isEnabled = true

                        return@setOnClickListener
                    }

                    val anchorDataList = mutableListOf<JSONObject>()

                    // Iterate over anchorsList
                    for ((anchorNode, selectedModel, scaling) in anchorsList) {
                        val session = sceneView.session ?: continue

                        if (anchorNode != null) {
                            sceneView.addChildNode(CloudAnchorNode(sceneView.engine, anchorNode.anchor).apply {
                                isScaleEditable = false

                                isRotationEditable=false
                                host(session) { cloudAnchorId, state ->
                                    Log.d("CloudAnchor", "STATE: $state, CloudAnchorId: $cloudAnchorId")
                                    when (state) {
                                        Anchor.CloudAnchorState.SUCCESS -> {
                                            Log.d("CloudAnchor", "Cloud anchor hosted successfully: $cloudAnchorId")

                                            // Create a JSON object for the anchor data
                                            val anchorData = JSONObject().apply {
                                                put("anchor_id", cloudAnchorId)
                                                put("model", selectedModel)
                                                put("scaling", scaling)
                                            }

                                            // Add the anchor data to the list
                                            anchorDataList.add(anchorData)

                                            Log.d("Actual Anchor Data List", "$anchorDataList")


                                            // Check if all anchors are hosted successfully
                                            if (anchorDataList.size == anchorsList.size) {
                                                // All anchors hosted, send the data to the server
                                                val projectTitle = intent.getStringExtra("projectTitle")

                                                // Create a JSON object to send to the server
                                                val requestBody = JSONObject().apply {
                                                    put("anchors", anchorDataList)
                                                    put("project_title", projectTitle)
                                                }

                                                val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                                                val authToken = sharedPreferences.getString("jwtToken", "")

                                                // Make a POST request to the Flask /anchors endpoint
                                                val client = OkHttpClient()
                                                val request = Request.Builder()
                                                    .url("https://frafortu.pythonanywhere.com/project")
                                                    .header("Content-Type", "application/json")
                                                    .header("Authorization", "Bearer $authToken") // Include the JWT in the Authorization header
                                                    .post(RequestBody.create("application/json".toMediaTypeOrNull(), requestBody.toString()))
                                                    .build()

                                                client.newCall(request).enqueue(object : Callback {
                                                    override fun onFailure(call: Call, e: IOException) {
                                                        e.printStackTrace()
                                                        // Handle failure
                                                    }

                                                    override fun onResponse(call: Call, response: Response) {
                                                        // Handle the response from the server
                                                        val responseBody = response.body?.string()
                                                        Log.d("Response", responseBody ?: "Response body is null")

                                                        try {
                                                            val jsonResponse = JSONObject(responseBody)
                                                            val success = jsonResponse.optBoolean("success", false)

                                                            if (success) {
                                                                // Show a success Toast
                                                                runOnUiThread {
                                                                    Toast.makeText(context, "Operation successful", Toast.LENGTH_SHORT).show()
                                                                }
                                                            } else {
                                                                // Show a failure Toast or handle the failure case as needed
                                                                runOnUiThread {
                                                                    Toast.makeText(context, "Operation failed", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        } catch (e: JSONException) {
                                                            e.printStackTrace()
                                                            // Handle JSON parsing error
                                                        } finally {
                                                            // Enable the button after processing the response
                                                            runOnUiThread {
                                                                isClickable = true
                                                                isEnabled = true
                                                            }
                                                        }
                                                    }
                                                })
                                            }
                                        }

                                        else -> {
                                            Log.d("CloudAnchor", "Cloud anchor hosting failed: $cloudAnchorId")
                                            val failureToast = Toast.makeText(
                                                context,
                                                "Cloud anchor hosting failed: $cloudAnchorId",
                                                Toast.LENGTH_LONG
                                            )
                                            failureToast.show()
                                            // Enable the button after showing the toast
                                            runOnUiThread {
                                                isClickable = true
                                                isEnabled = true
                                            }
                                        }
                                    }
                                }
                            })
                        }
                    }

                }
            }
        }


        b = findViewById<ImageButton?>(R.id.button1).apply { setOnClickListener{kmodel="https://firebasestorage.googleapis.com/v0/b/mac-proj-5f6eb.appspot.com/o/black_sofa.glb?alt=media&token=fbb4bcd3-388a-42bf-b328-4f2911aac288"}  }
        b = findViewById<ImageButton?>(R.id.button2).apply { setOnClickListener{kmodel="https://firebasestorage.googleapis.com/v0/b/mac-proj-5f6eb.appspot.com/o/folding_table.glb?alt=media&token=23630db0-702c-44d9-afe5-64c279d77d6a"}  }
        b = findViewById<ImageButton?>(R.id.button3).apply { setOnClickListener{kmodel="https://firebasestorage.googleapis.com/v0/b/mac-proj-5f6eb.appspot.com/o/office_chair.glb?alt=media&token=52fa1f98-1eda-4774-9df2-35896d6f4d9a"}  }
        b = findViewById<ImageButton?>(R.id.button4).apply { setOnClickListener{kmodel="https://firebasestorage.googleapis.com/v0/b/mac-proj-5f6eb.appspot.com/o/black_vase.glb?alt=media&token=baceaaed-f61d-4e0a-8d1d-565f4a37896a"}  }
        b = findViewById<ImageButton?>(R.id.button5).apply { setOnClickListener{kmodel="https://firebasestorage.googleapis.com/v0/b/mac-proj-5f6eb.appspot.com/o/sideboard.glb?alt=media&token=c52d7da0-fb02-43c4-92bb-1ec48ca13293"}  }
        b = findViewById<ImageButton?>(R.id.button6).apply { setOnClickListener{kmodel="https://firebasestorage.googleapis.com/v0/b/mac-proj-5f6eb.appspot.com/o/sofa1.glb?alt=media&token=2e59a4a5-f09b-4185-953b-6b7ac7b848fc"}  }


        horiz_hide_show = findViewById(R.id.buttonsContainer)

        button_hide_show = findViewById<Button?>(R.id.btn).apply {
            setOnClickListener {
                if (!vis) {
                    // Hide menu and rotate button
                    ObjectAnimator.ofFloat(this, "rotation", 0f).start()
                    animate().translationY(200f)
                    horiz_hide_show.animate().translationY(horiz_hide_show.height.toFloat())
                        .withEndAction {
                            horiz_hide_show.visibility = View.GONE
                        }
                } else {
                    // Show menu and rotate button
                    ObjectAnimator.ofFloat(this, "rotation", 180f).start()
                    animate().translationY(0f)
                    horiz_hide_show.visibility = View.VISIBLE
                    horiz_hide_show.animate().translationY(0f)
                        .withEndAction{
                        }
                }
                vis = !vis
            }
        }
    }

    fun addAnchorNode(anchor: Anchor, scaling: Float3?) {
        val selectedModel = kmodel  // Save the current selected model

        // Add the anchor node with the model node attached
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor)
                .apply {
                    isScaleEditable = false
                    isEditable = true
                    isPositionEditable = true
                    isRotationEditable = false

                    lifecycleScope.launch {
                        isLoading = true
                        sceneView.modelLoader.loadModelInstance(selectedModel)?.let { modelInstance ->
                            val modelNode = ModelNode(
                                modelInstance = modelInstance,
                                scaleToUnits = null,
                                centerOrigin = null
                            ).apply {
                                isShadowCaster = true  // Enable casting shadows
                                isShadowReceiver = true  // Enable receiving shadows
                                isEditable = true
                                isRotationEditable = false
                                scaling?.let {
                                    this.scale = it
                                }
                            }

                            // Add the model node as a child of the anchor node
                            addChildNode(modelNode)
                        }
                        isLoading = false
                        isRotationEditable = false
                    }
                    anchorNode = this
                    val newAnchorTriple = Triple(this, selectedModel, scaling)
                    anchorsList.add(newAnchorTriple as Triple<AnchorNode?, String, Float3>)
                    Log.d("AnchorsList", "Added new anchor: $newAnchorTriple. AnchorsList: $anchorsList")
                    Log.d("AnchorsList", "new anchor pose: ${anchor.pose}")

                }
        )

        // Add the anchor and the selected model to the list

        // Log the contents of the anchorsList
    }

    fun addModelToFirebase(modelString: String,anchor: Anchor) {
        val session = sceneView.session
        val frame = sceneView.frame

        if (frame != null) {
            if (sceneView.session?.estimateFeatureMapQualityForHosting(frame.camera.pose) == Session.FeatureMapQuality.INSUFFICIENT) {
                val insufficientVisualDataToast = Toast.makeText(
                    this,
                    R.string.insufficient_visual_data,
                    Toast.LENGTH_LONG
                )
                insufficientVisualDataToast.show()
                Log.d("CloudAnchor", "Insufficient visual data for hosting")
                // Enable the button after showing the toast
            }
            else{
                sceneView.addChildNode(CloudAnchorNode(sceneView.engine, anchorNode!!.anchor).apply {
                    isScaleEditable = false

                    isRotationEditable = false
                    if (session != null) {
                        host(session) { cloudAnchorId, state ->
                            Log.d("CloudAnchor", "STATE: $state, CloudAnchorId: $cloudAnchorId")
                            when (state) {
                                Anchor.CloudAnchorState.SUCCESS -> {
                                    Log.d(
                                        "CloudAnchor",
                                        "Cloud anchor hosted successfully: $cloudAnchorId"
                                    )
                                    val newModelKey = databaseReference.push().key!!
                                    val anchorString = anchor.toString()
                                    val cloudAnchorString = cloudAnchorId.toString()

                                    val newModelMap = mapOf(
                                        "name" to modelString,
                                        "anchor" to anchorString,
                                        "cloudAnchor" to cloudAnchorString
                                        // Add other model properties if needed
                                    )

                                    databaseReference.child(newModelKey).setValue(newModelMap)
                                    instructionText.text = "Session Updated"


                                }

                                else -> {}
                            }

                       }
                    }
                })
            }
        }
    }

    fun deleteModelFromFirebase(model: Node,anchor: Anchor){
        val modelString = model.toString()
        val anchorString = anchor.toString()
        val query: Query = databaseReference.orderByChild("anchor").equalTo(anchorString)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (modelSnapshot in dataSnapshot.children) {
                    val modelAnchor = modelSnapshot.child("anchor").getValue(String::class.java)

                    if (modelAnchor != null && modelAnchor == anchorString) {
                        // Remove the model from the database
                        modelSnapshot.ref.removeValue()
                        println("Model deleted from session successfully!")
                        instructionText.text = "Session Updated"
                        return
                    }
                }
                println("Model not found in the session")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("Firebase", "loadData:onCancelled", error.toException())
            }
        })
    }

    // Function to update the corresponding anchor field in Firebase
    fun updateFirebaseAnchor(oldAnchor: Anchor, newAnchor: Anchor, cloudAnchorId: String) {
        Log.d("UPDATE", "UPDATING $oldAnchor in $newAnchor")
        // Find the entry in Firebase corresponding to the old anchor
        val query = databaseReference.orderByChild("anchor").equalTo(oldAnchor.toString())
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (modelSnapshot in dataSnapshot.children) {
                    // Update the cloudAnchor field with the new cloud anchor ID
                    modelSnapshot.ref.child("cloudAnchor").setValue(cloudAnchorId)
                    // Update the anchor field with the new anchor's details
                    modelSnapshot.ref.child("anchor").setValue(newAnchor.toString())
                }
                instructionText.text = "Session Updated"

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle database error
                Log.e("Firebase", "updateFirebaseAnchor:onCancelled", databaseError.toException())
            }
        })
    }


    fun parseFloat3FromString(input: String): Float3? {
        try {
            // Extract values from the string
            val regex = Regex("Float3\\(x=(-?\\d+\\.\\d+), y=(-?\\d+\\.\\d+), z=(-?\\d+\\.\\d+)\\)")
            val matchResult = regex.find(input)
            Log.d("MATCH","Result: $matchResult")
            if (matchResult != null) {
                val (x, y, z) = matchResult.destructured
                // Create a Float3 object
                val float3 = Float3(x.toFloat(), y.toFloat(), z.toFloat())

                // Log the successfully parsed Float3
                Log.d("parseFloat3", "Successfully parsed Float3: $float3")

                return float3
            }
        } catch (e: Exception) {
            // Log any exceptions that occurred during parsing
            Log.e("parseFloat3", "Error parsing Float3 from input: $input", e)
        }

        // Log that parsing failed and return null
        Log.d("parseFloat3", "Failed to parse Float3 from input: $input")
        return null
    }


    fun Fragment.setFullScreen(
        fullScreen: Boolean = true,
        hideSystemBars: Boolean = true,
        fitsSystemWindows: Boolean = true
    ) {
        requireActivity().setFullScreen(
            this.requireView(),
            fullScreen,
            hideSystemBars,
            fitsSystemWindows
        )
    }

    fun Activity.setFullScreen(
        rootView: View,
        fullScreen: Boolean = true,
        hideSystemBars: Boolean = true,
        fitsSystemWindows: Boolean = true
    ) {
        rootView.viewTreeObserver?.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                WindowCompat.setDecorFitsSystemWindows(window, fitsSystemWindows)
                WindowInsetsControllerCompat(window, rootView).apply {
                    if (hideSystemBars) {
                        if (fullScreen) {
                            hide(
                                WindowInsetsCompat.Type.statusBars() or
                                        WindowInsetsCompat.Type.navigationBars()
                            )
                        } else {
                            show(
                                WindowInsetsCompat.Type.statusBars() or
                                        WindowInsetsCompat.Type.navigationBars()
                            )
                        }
                        systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            }
        }
    }

    fun View.doOnApplyWindowInsets(action: (systemBarsInsets: Insets) -> Unit) {
        doOnAttach {
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                action(insets.getInsets(WindowInsetsCompat.Type.systemBars()))
                WindowInsetsCompat.CONSUMED
            }
        }
    }


}


