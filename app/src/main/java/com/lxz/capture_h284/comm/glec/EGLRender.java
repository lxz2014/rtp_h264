package com.lxz.capture_h284.comm.glec;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import java.nio.IntBuffer;


public class EGLRender implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "EncodeDecodeSurface";
    private static final boolean VERBOSE = false;           // lots of logging

    private STextureRender mTextureRender;
    private SurfaceTexture mSurfaceTexture;

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLContext mEGLContextEncoder = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    private EGLSurface mEGLSurfaceEncoder = EGL14.EGL_NO_SURFACE;

    private Surface decodeSurface;

    private int mWidth;
    private int mHeight;
    private int fps;
    private int video_interval;
    private boolean mFrameAvailable = true;

    public EGLRender(Surface surface, int mWidth, int mHeight, int fps) {
        this.mWidth = mWidth;
        this.mHeight = mHeight;
        initFPs(fps);
        eglSetup(surface);
        makeCurrent();
        setup();
    }

    private void initFPs(int fps) {
        this.fps = fps;
        video_interval = 1000 / fps;
    }

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports pbuffer.
     */
    private void eglSetup(Surface surface) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }

        // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }

        EGLConfig configEncoder = getConfig(2);

        // Configure context for OpenGL ES 2.0.
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attrib_list, 0);
        checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }

        mEGLContextEncoder = EGL14.eglCreateContext(mEGLDisplay, configEncoder, mEGLContext,
                attrib_list, 0);
        checkEglError("eglCreateContext");
        if (mEGLContextEncoder == null) {
            throw new RuntimeException("null context2");
        }

        // Create a pbuffer surface.
        int[] surfaceAttribs = {
                EGL14.EGL_WIDTH, mWidth,
                EGL14.EGL_HEIGHT, mHeight,
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs, 0);


        checkEglError("eglCreatePbufferSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }


        int[] surfaceAttribs2 = {
                EGL14.EGL_NONE
        };
        mEGLSurfaceEncoder = EGL14.eglCreateWindowSurface(mEGLDisplay, configEncoder, surface,
                surfaceAttribs2, 0);   //creates an EGL window surface and returns its handle
        checkEglError("eglCreateWindowSurface");
        if (mEGLSurfaceEncoder == null) {
            throw new RuntimeException("surface was null");
        }
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    /**
     * Creates interconnected instances of TextureRender, SurfaceTexture, and Surface.
     */
    private void setup() {
        mTextureRender = new STextureRender(mWidth, mHeight);
        mTextureRender.surfaceCreated();

        if (VERBOSE) Log.d(TAG, "textureID=" + mTextureRender.getTextureId());
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        decodeSurface = new Surface(mSurfaceTexture);
    }

    public Surface getDecodeSurface() {
        return decodeSurface;
    }

    private EGLConfig getConfig(int version) {
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;
        if (version >= 3) {
            renderableType |= EGLExt.EGL_OPENGL_ES3_BIT_KHR;
        }

        // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
        // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
        // when reading into a GL_RGBA buffer.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            Log.w(TAG, "unable to find RGB8888 / " + version + " EGLConfig");
            return null;
        }
        return configs[0];
    }

    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    public void makeCurrent(int index) {
        if (index == 0) {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
        } else {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurfaceEncoder, mEGLSurfaceEncoder, mEGLContextEncoder)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
        }

    }

    public void setPresentationTime() {
        long nsecs = computePresentationTimeNsec(count++);
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurfaceEncoder, nsecs);
        checkEglError("eglPresentationTimeANDROID");
    }

    public void awaitNewImage() {
        if (mFrameAvailable) {
            mFrameAvailable = false;
            mSurfaceTexture.updateTexImage();
        }
    }

    public boolean swapBuffers() {
        boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurfaceEncoder);
        checkEglError("eglSwapBuffers");
        return result;
    }

    private int count = 1;

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mFrameAvailable = true;
    }

    public long computePresentationTimeNsec(int frameIndex) {
        final long ONE_BILLION = 1000000000;
        return frameIndex * ONE_BILLION / fps;
    }
    /**
     * 开始录屏
     */
    public void drawImage() {
        // Log.e("lc","正在绘制新的图像 " + new Date(System.currentTimeMillis()));
        mTextureRender.drawFrame();
    }

//    public void start() {
//        start = true;
//        while (start) {
//            makeCurrent(1);
//            awaitNewImage();
//            current_time = System.currentTimeMillis();
//            if (current_time - time >= video_interval) {
//                drawImage();
//                callBack.onUpdate();
//                setPresentationTime();
//                swapBuffers();
//                time = current_time;
//            }
//        }
//    }

    public int getVideo_interval() {
        return video_interval;
    }
}
