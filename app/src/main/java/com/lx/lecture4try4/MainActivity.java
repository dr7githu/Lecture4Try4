package com.lx.lecture4try4;

import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

public class MainActivity extends AppCompatActivity {

    private CustomArFragment fragment;
    private Anchor cloudAnchor;

    private enum AppAnchorState {
        NONE,
        HOSTING,
        HOSTED
    }

    private AppAnchorState appAnchorState = AppAnchorState.NONE;

    private SnackbarHelper snackbarHelper = new SnackbarHelper();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragment = (CustomArFragment)getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        fragment.getPlaneDiscoveryController().hide();
        fragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

        Button clearButton = (Button)findViewById(R.id.clear_button);
        Button resolveButton = (Button)findViewById(R.id.resolve_button);

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCloudAnchor(null);
            }
        });

        resolveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        fragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {

                    if(plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING ||
                            appAnchorState != AppAnchorState.NONE) {
                        return;
                    }

                    Anchor newAnchor = fragment.getArSceneView().getSession().hostCloudAnchor(hitResult.createAnchor());

                    setCloudAnchor(newAnchor);

                    appAnchorState = AppAnchorState.HOSTING;
                    snackbarHelper.showMessage(this, "Now hosting anchor...");

                    placeObject(fragment, cloudAnchor, Uri.parse("ArcticFox_Posed.sfb"));

                    addLineBetweenPoints(fragment.getArSceneView().getScene(), new Vector3(0.2f, 0.2f, 0.2f), new Vector3(0.0f, 0.15f, 0.0f));
                }
        );

    }

    private void setCloudAnchor(Anchor newAnchor) {
        if (cloudAnchor != null) {
            cloudAnchor.detach();
        }

        cloudAnchor = newAnchor;
        appAnchorState = AppAnchorState.NONE;
        snackbarHelper.hide(this);
    }

    private void onUpdateFrame(FrameTime frameTime) {
        checkUpdateAnchor();
    }

    private synchronized void checkUpdateAnchor(){
        if(appAnchorState != AppAnchorState.HOSTING) {
            return;
        }
        Anchor.CloudAnchorState cloudState = cloudAnchor.getCloudAnchorState();
        if(cloudState.isError()) {
            snackbarHelper.showMessage(this, "Error hosting anchor.." + cloudState);
            appAnchorState = AppAnchorState.NONE;
        } else if(cloudState == Anchor.CloudAnchorState.SUCCESS) {
            snackbarHelper.showMessage(this, "Anchor hosted Cloud ID : " + cloudAnchor.getCloudAnchorId());
            appAnchorState = AppAnchorState.HOSTED;
        }
    }

    private void placeObject(ArFragment fragment, Anchor anchor, Uri model) {
        ModelRenderable.builder()
                .setSource(fragment.getContext(), model)
                .build()
                .thenAccept(renderable -> addNodeToScene(fragment, anchor, renderable))
                .exceptionally((throwable -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(throwable.getMessage())
                            .setTitle("Error!");
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    return null;
                }));
    }

    private void addNodeToScene(ArFragment fragment, Anchor anchor, Renderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        node.setRenderable(renderable);
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        node.select();
    }


    private void addLineBetweenPoints(Scene scene, Vector3 from, Vector3 to) {
        // prepare an anchor position
        Quaternion camQ = scene.getCamera().getWorldRotation();

        float[] f1 = new float[]{to.x, to.y, to.z};
        float[] f2 = new float[]{camQ.x, camQ.y, camQ.z, camQ.w};
        Pose anchorPose = new Pose(f1, f2);

        // make an ARCore Anchor
//        Anchor anchor = mCallback.getSession().createAnchor(anchorPose);
        Anchor anchor = fragment.getArSceneView().getSession().createAnchor(anchorPose);

        // Node that is automatically positioned in world space based on the ARCore Anchor.
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(scene);

        // Compute a line's length
        float lineLength = Vector3.subtract(from, to).length();

        // Prepare a color
        Color colorOrange = new Color(android.graphics.Color.parseColor("#ffa71c"));

        // 1. make a material by the color
        MaterialFactory.makeOpaqueWithColor(fragment.getContext(), colorOrange)
                .thenAccept(material -> {
                    // 2. make a model by the material
                    ModelRenderable model = ShapeFactory.makeCylinder(0.0025f, lineLength,
                            new Vector3(0f, lineLength / 2, 0f), material);
                    model.setShadowReceiver(false);
                    model.setShadowCaster(false);

                    // 3. make node
                    Node node = new Node();
                    node.setRenderable(model);
                    node.setParent(anchorNode);

                    // 4. set rotation
                    final Vector3 difference = Vector3.subtract(to, from);
                    final Vector3 directionFromTopToBottom = difference.normalized();
                    final Quaternion rotationFromAToB =
                            Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
                    node.setWorldRotation(Quaternion.multiply(rotationFromAToB,
                            Quaternion.axisAngle(new Vector3(1.0f, 0.0f, 0.0f), 90)));
                });
    }

/*

    private void addLineBetweenHits(HitResult hitResult, Plane plane, MotionEvent motionEvent) {

        int val = motionEvent.getActionMasked();
        float axisVal = motionEvent.getAxisValue(MotionEvent.AXIS_X, motionEvent.getPointerId(motionEvent.getPointerCount() - 1));
        Log.e("Values:", String.valueOf(val) + String.valueOf(axisVal));
        Anchor anchor = hitResult.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);


        if (lastAnchorNode != null) {
            anchorNode.setParent(fragment.getArSceneView().getScene());
            Vector3 point1, point2;
            point1 = lastAnchorNode.getWorldPosition();
            point2 = anchorNode.getWorldPosition();

    */
/*
        First, find the vector extending between the two points and define a look rotation
        in terms of this Vector.
    *//*

            final Vector3 difference = Vector3.subtract(point1, point2);
            final Vector3 directionFromTopToBottom = difference.normalized();
            final Quaternion rotationFromAToB =
                    Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
            MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new Color(0, 255, 244))
                    .thenAccept(
                            material -> {
                            */
/* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
                                   to extend to the necessary length.  *//*

                                ModelRenderable model = ShapeFactory.makeCube(
                                        new Vector3(.01f, .01f, difference.length()),
                                        Vector3.zero(), material);
                            */
/* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
                                   the midpoint between the given points . *//*

                                Node node = new Node();
                                node.setParent(anchorNode);
                                node.setRenderable(model);
                                node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                                node.setWorldRotation(rotationFromAToB);
                            }
                    );
            lastAnchorNode = anchorNode;
        }
    }
*/


} // End of class
