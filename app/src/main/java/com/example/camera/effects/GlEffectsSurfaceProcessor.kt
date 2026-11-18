package com.example.camera.effects

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import com.example.camera.model.ColorFilterPreset
import com.example.camera.model.EffectsSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

/**
 * Hardware-accelerated OpenGL ES 2.0 SurfaceProcessor for real-time CameraX Preview and VideoCapture.
 * Applies Color Filters, Beauty skin smoothing, and Background/Portrait Blur cleanly on GPU
 * without rebinding CameraX use cases or interrupting active video recording.
 */
class GlEffectsSurfaceProcessor : SurfaceProcessor {

    data class GlEffectUniforms(
        val blurStrength: Float = 0f,
        val beautyStrength: Float = 0f,
        val filterOrdinal: Int = 0,
    )

    @Volatile
    private var uniforms = GlEffectUniforms()

    private val glThread = HandlerThread("LumaCamGL").apply { start() }
    private val glHandler = Handler(glThread.looper)
    val glExecutor: Executor = Executor { command ->
        if (!released) {
            glHandler.post(command)
        }
    }

    @Volatile
    private var released = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var pbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var programId: Int = 0
    private var oesTextureId: Int = 0
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    private var aPositionLoc: Int = -1
    private var aTexCoordLoc: Int = -1
    private var uTexMatrixLoc: Int = -1
    private var uTextureLoc: Int = -1
    private var uTexelSizeLoc: Int = -1
    private var uBlurStrengthLoc: Int = -1
    private var uBeautyStrengthLoc: Int = -1
    private var uFilterModeLoc: Int = -1

    private val outputSurfaces = LinkedHashMap<SurfaceOutput, EGLSurface>()
    private val inputMatrix = FloatArray(16)
    private val outputMatrix = FloatArray(16)

    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(QUAD_VERTICES)
        .apply { position(0) }

    private val texCoordBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_TEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(QUAD_TEX_COORDS)
        .apply { position(0) }

    @Volatile
    private var glInitialized = false

    fun updateEffects(settings: EffectsSettings, isPhotoHardwareBokehActive: Boolean) {
        val effectiveBlur = when {
            isPhotoHardwareBokehActive -> 0f // Hardware Bokeh Extension handles blur directly
            settings.portraitEnabled -> maxOf(settings.backgroundBlurStrength, 0.72f)
            else -> settings.backgroundBlurStrength
        }
        uniforms = GlEffectUniforms(
            blurStrength = effectiveBlur.coerceIn(0f, 1f),
            beautyStrength = settings.beautyStrength.coerceIn(0f, 1f),
            filterOrdinal = when (settings.colorFilter) {
                ColorFilterPreset.NONE -> 0
                ColorFilterPreset.VIVID -> 1
                ColorFilterPreset.CINEMA -> 2
                ColorFilterPreset.NOIR -> 3
                ColorFilterPreset.WARM_FILM -> 4
                ColorFilterPreset.COOL_MIST -> 5
            },
        )
    }

    override fun onInputSurface(request: SurfaceRequest) {
        if (released) {
            request.willNotProvideSurface()
            return
        }
        glHandler.post {
            if (!glInitialized && !released) {
                initGl()
                glInitialized = true
            }
            if (released || eglDisplay == EGL14.EGL_NO_DISPLAY) {
                request.willNotProvideSurface()
                return@post
            }
            makePbufferCurrent()
            releaseInputSurfaceOnly()

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            oesTextureId = texIds[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE,
            )

            val st = SurfaceTexture(oesTextureId).apply {
                setDefaultBufferSize(request.resolution.width, request.resolution.height)
                setOnFrameAvailableListener({ renderCurrentFrame() }, glHandler)
            }
            val surface = Surface(st)
            inputSurfaceTexture = st
            inputSurface = surface

            request.provideSurface(surface, glExecutor) {
                glHandler.post {
                    if (inputSurface == surface) {
                        releaseInputSurfaceOnly()
                    }
                }
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (released) {
            surfaceOutput.close()
            return
        }
        glHandler.post {
            if (released || eglDisplay == EGL14.EGL_NO_DISPLAY) {
                surfaceOutput.close()
                return@post
            }
            val surface = surfaceOutput.getSurface(glExecutor) {
                glHandler.post {
                    removeOutputSurface(surfaceOutput)
                }
            }
            val config = eglConfig
            if (config == null) {
                surfaceOutput.close()
                return@post
            }
            val eglSurface = runCatching {
                EGL14.eglCreateWindowSurface(
                    eglDisplay,
                    config,
                    surface,
                    intArrayOf(EGL14.EGL_NONE),
                    0,
                )
            }.getOrNull() ?: EGL14.EGL_NO_SURFACE

            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                surfaceOutput.close()
                return@post
            }
            outputSurfaces[surfaceOutput] = eglSurface
        }
    }

    fun createCameraEffect(targets: Int): CameraEffect {
        return object : CameraEffect(
            targets,
            glExecutor,
            this,
            { error -> Log.e(TAG, "CameraEffect error", error) },
        ) {}
    }

    fun release() {
        if (released) return
        released = true
        glHandler.post {
            outputSurfaces.keys.toList().forEach { removeOutputSurface(it) }
            releaseInputSurfaceOnly()
            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
            }
            if (pbufferSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, pbufferSurface)
                pbufferSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
            glThread.quitSafely()
        }
    }

    private fun initGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "Unable to get EGL14 display")
            return
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "Unable to initialize EGL14")
            return
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) ||
            numConfigs[0] <= 0
        ) {
            Log.e(TAG, "Unable to choose EGLConfig")
            return
        }
        eglConfig = configs[0]

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            contextAttribs,
            0,
        )

        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE,
        )
        pbufferSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        makePbufferCurrent()

        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
        uTexMatrixLoc = GLES20.glGetUniformLocation(programId, "uTexMatrix")
        uTextureLoc = GLES20.glGetUniformLocation(programId, "sTexture")
        uTexelSizeLoc = GLES20.glGetUniformLocation(programId, "uTexelSize")
        uBlurStrengthLoc = GLES20.glGetUniformLocation(programId, "uBlurStrength")
        uBeautyStrengthLoc = GLES20.glGetUniformLocation(programId, "uBeautyStrength")
        uFilterModeLoc = GLES20.glGetUniformLocation(programId, "uFilterMode")
    }

    private fun makePbufferCurrent() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && pbufferSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)
        }
    }

    private fun releaseInputSurfaceOnly() {
        inputSurface?.release()
        inputSurface = null
        inputSurfaceTexture?.setOnFrameAvailableListener(null)
        inputSurfaceTexture?.release()
        inputSurfaceTexture = null
        if (oesTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
            oesTextureId = 0
        }
    }

    private fun removeOutputSurface(surfaceOutput: SurfaceOutput) {
        val eglSurface = outputSurfaces.remove(surfaceOutput)
        if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
            makePbufferCurrent()
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
        }
        runCatching { surfaceOutput.close() }
    }

    private fun renderCurrentFrame() {
        val st = inputSurfaceTexture ?: return
        if (released || programId == 0) return

        makePbufferCurrent()
        runCatching { st.updateTexImage() }.onFailure { return }
        st.getTransformMatrix(inputMatrix)

        val currentUniforms = uniforms
        val timestampNs = st.timestamp

        for ((surfaceOutput, eglSurface) in outputSurfaces.entries.toList()) {
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                continue
            }
            Matrix.setIdentityM(outputMatrix, 0)
            surfaceOutput.updateTransformMatrix(outputMatrix, inputMatrix)

            val outW = surfaceOutput.size.width.coerceAtLeast(1)
            val outH = surfaceOutput.size.height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, outW, outH)

            GLES20.glUseProgram(programId)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glUniform1i(uTextureLoc, 0)

            GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, outputMatrix, 0)
            GLES20.glUniform2f(uTexelSizeLoc, 1f / outW.toFloat(), 1f / outH.toFloat())
            GLES20.glUniform1f(uBlurStrengthLoc, currentUniforms.blurStrength)
            GLES20.glUniform1f(uBeautyStrengthLoc, currentUniforms.beautyStrength)
            GLES20.glUniform1i(uFilterModeLoc, currentUniforms.filterOrdinal)

            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aPositionLoc)
            GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            texCoordBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aTexCoordLoc)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPositionLoc)
            GLES20.glDisableVertexAttribArray(aTexCoordLoc)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, timestampNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val TAG = "GlEffectsProcessor"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )

        private val QUAD_TEX_COORDS = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            varying vec2 vNormalizedPos;
            void main() {
                gl_Position = aPosition;
                vNormalizedPos = aPosition.xy * 0.5 + 0.5;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            uniform vec2 uTexelSize;
            uniform float uBlurStrength;
            uniform float uBeautyStrength;
            uniform int uFilterMode;
            varying vec2 vTexCoord;
            varying vec2 vNormalizedPos;

            vec3 applyColorFilter(vec3 color, int mode) {
                if (mode == 1) {
                    // Vivid: punchy saturation & contrast
                    float luma = dot(color, vec3(0.299, 0.587, 0.114));
                    vec3 sat = mix(vec3(luma), color, 1.32);
                    return clamp((sat - 0.5) * 1.10 + 0.5, 0.0, 1.0);
                } else if (mode == 2) {
                    // Cinema: rich teal shadows & warm amber highlights
                    float luma = dot(color, vec3(0.299, 0.587, 0.114));
                    vec3 shadows = vec3(0.0, 0.04, 0.08) * (1.0 - luma);
                    vec3 highlights = vec3(0.08, 0.04, -0.03) * luma;
                    vec3 graded = (color - 0.5) * 1.12 + 0.5 + shadows + highlights;
                    return clamp(graded, 0.0, 1.0);
                } else if (mode == 3) {
                    // Noir: crisp silver-gelatin monochrome
                    float luma = dot(color, vec3(0.299, 0.587, 0.114));
                    float contrast = clamp((luma - 0.5) * 1.24 + 0.5, 0.0, 1.0);
                    return vec3(contrast);
                } else if (mode == 4) {
                    // Warm Film: golden nostalgia
                    vec3 warm = color * vec3(1.08, 1.02, 0.88) + vec3(0.02, 0.01, 0.0);
                    return clamp(warm, 0.0, 1.0);
                } else if (mode == 5) {
                    // Cool Mist: crisp Nordic blue tint
                    float luma = dot(color, vec3(0.299, 0.587, 0.114));
                    vec3 desat = mix(vec3(luma), color, 0.82);
                    return clamp(desat * vec3(0.92, 0.99, 1.09), 0.0, 1.0);
                }
                return color;
            }

            void main() {
                vec3 baseColor = texture2D(sTexture, vTexCoord).rgb;
                vec3 result = baseColor;

                // 1. Beauty smoothing: edge-preserving bilateral approximation + soft glow
                if (uBeautyStrength > 0.01) {
                    vec2 step1 = uTexelSize * (2.5 + uBeautyStrength * 3.5);
                    vec3 s0 = texture2D(sTexture, vTexCoord + vec2(step1.x, 0.0)).rgb;
                    vec3 s1 = texture2D(sTexture, vTexCoord - vec2(step1.x, 0.0)).rgb;
                    vec3 s2 = texture2D(sTexture, vTexCoord + vec2(0.0, step1.y)).rgb;
                    vec3 s3 = texture2D(sTexture, vTexCoord - vec2(0.0, step1.y)).rgb;
                    vec3 smoothAvg = (baseColor + s0 + s1 + s2 + s3) * 0.2;
                    float edgeDiff = length(baseColor - smoothAvg);
                    float skinWeight = (1.0 - smoothstep(0.05, 0.22, edgeDiff)) * uBeautyStrength;
                    result = mix(result, smoothAvg + vec3(0.025, 0.018, 0.012) * uBeautyStrength, skinWeight * 0.85);
                }

                // 2. Portrait / Background Bokeh Blur: center-subject sharp focus with optical edge bokeh
                if (uBlurStrength > 0.01) {
                    vec2 centered = (vNormalizedPos - vec2(0.5, 0.46)) * vec2(1.0, 0.82);
                    float dist = length(centered);
                    float blurMask = smoothstep(0.20, 0.56, dist) * uBlurStrength;
                    if (blurMask > 0.01) {
                        float radius = blurMask * 11.0;
                        vec2 r = uTexelSize * radius;
                        vec3 acc = result;
                        acc += texture2D(sTexture, vTexCoord + vec2(r.x, 0.0)).rgb;
                        acc += texture2D(sTexture, vTexCoord - vec2(r.x, 0.0)).rgb;
                        acc += texture2D(sTexture, vTexCoord + vec2(0.0, r.y)).rgb;
                        acc += texture2D(sTexture, vTexCoord - vec2(0.0, r.y)).rgb;
                        acc += texture2D(sTexture, vTexCoord + vec2(r.x * 0.7, r.y * 0.7)).rgb;
                        acc += texture2D(sTexture, vTexCoord + vec2(-r.x * 0.7, r.y * 0.7)).rgb;
                        acc += texture2D(sTexture, vTexCoord + vec2(r.x * 0.7, -r.y * 0.7)).rgb;
                        acc += texture2D(sTexture, vTexCoord + vec2(-r.x * 0.7, -r.y * 0.7)).rgb;
                        vec3 blurred = acc / 9.0;
                        result = mix(result, blurred, blurMask);
                    }
                }

                // 3. Color Grading Filter
                if (uFilterMode != 0) {
                    result = applyColorFilter(result, uFilterMode);
                }

                gl_FragColor = vec4(result, 1.0);
            }
        """
    }
}
