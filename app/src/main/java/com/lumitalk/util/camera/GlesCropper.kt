package com.lumitalk.util.camera

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GlesCropper(
    private val srcWidth: Int,
    private val srcHeight: Int,
    private val roiWidth: Int,
    private val roiHeight: Int,
    private val roiSurface: Surface
) {
    private val renderThread = HandlerThread("GlesCropperThread").apply { start() }
    private val handler = Handler(renderThread.looper)

    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var previewEglSurface = EGL14.EGL_NO_SURFACE
    private var roiEglSurface = EGL14.EGL_NO_SURFACE

    private var program = 0
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    var cameraSurface: Surface? = null
        private set

    private var previewSurface: Surface? = null

    private val transformMatrix = FloatArray(16)
    private val cropMatrix = FloatArray(16)

    private var frameCounter = 0

    fun init(onInitialized: () -> Unit) {
        handler.post {
            setupEgl()
            setupGl()
            onInitialized()
        }
    }

    fun setPreviewSurface(surface: Surface) {
        handler.post {
            previewSurface = surface
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                if (previewEglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, previewEglSurface)
                }
                val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                previewEglSurface = EGL14.eglCreateWindowSurface(eglDisplay, getEglConfig(), surface, surfaceAttribs, 0)
                
                EGL14.eglMakeCurrent(eglDisplay, previewEglSurface, previewEglSurface, eglContext)
                EGL14.eglSwapInterval(eglDisplay, 0)
                
                EGL14.eglMakeCurrent(eglDisplay, roiEglSurface, roiEglSurface, eglContext)
            }
        }
    }

    private fun setupEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val config = getEglConfig()
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        roiEglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, roiSurface, surfaceAttribs, 0)
        
        EGL14.eglMakeCurrent(eglDisplay, roiEglSurface, roiEglSurface, eglContext)
    }

    private fun getEglConfig(): android.opengl.EGLConfig? {
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
        return configs[0]
    }

    private fun setupGl() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(srcWidth, srcHeight)
            setOnFrameAvailableListener {
                handler.post { drawFrame() }
            }
        }
        cameraSurface = Surface(surfaceTexture)
    }

    private fun drawFrame() {
        surfaceTexture?.updateTexImage()
        surfaceTexture?.getTransformMatrix(transformMatrix)
        val timestamp = surfaceTexture?.timestamp ?: 0L

        frameCounter++

        if (previewEglSurface != EGL14.EGL_NO_SURFACE && frameCounter % 4 == 0) { // プレビューは 30fps
            EGL14.eglMakeCurrent(eglDisplay, previewEglSurface, previewEglSurface, eglContext)
            GLES20.glViewport(0, 0, srcWidth, srcHeight)
            renderTexture(transformMatrix)
            EGL14.eglSwapBuffers(eglDisplay, previewEglSurface)
        }

        if (roiEglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, roiEglSurface, roiEglSurface, eglContext)
            GLES20.glViewport(0, 0, roiWidth, roiHeight)

            val scaleX = srcWidth.toFloat() / roiWidth.toFloat()
            val scaleY = srcHeight.toFloat() / roiHeight.toFloat()
            Matrix.setIdentityM(cropMatrix, 0)
            Matrix.translateM(cropMatrix, 0, 0.5f - (0.5f * scaleX), 0.5f - (0.5f * scaleY), 0f)
            Matrix.scaleM(cropMatrix, 0, scaleX, scaleY, 1f)
            Matrix.multiplyMM(cropMatrix, 0, cropMatrix, 0, transformMatrix, 0)

            renderTexture(cropMatrix)
            
            EGLExt.eglPresentationTimeANDROID(eglDisplay, roiEglSurface, timestamp)
            EGL14.eglSwapBuffers(eglDisplay, roiEglSurface)
        }
    }

    private fun renderTexture(matrix: FloatArray) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        val matrixHandle = GLES20.glGetUniformLocation(program, "uMatrix")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, VERTEX_BUFFER)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, TEX_COORD_BUFFER)

        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, matrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    fun release() {
        handler.post {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (roiEglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, roiEglSurface)
                if (previewEglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, previewEglSurface)
                }
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            surfaceTexture?.release()
            cameraSurface?.release()
            renderThread.quitSafely()
        }
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private val VERTEX_COORDS = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private val TEX_COORDS = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

        private val VERTEX_BUFFER = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(VERTEX_COORDS); position(0) }
        private val TEX_COORD_BUFFER = ByteBuffer.allocateDirect(TEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(TEX_COORDS); position(0) }

        private const val VERTEX_SHADER_CODE = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
