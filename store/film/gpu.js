// The film drawn on the GPU, with nothing read back: the page is laid out as ever (film.js), and Chrome's
// html-in-canvas (`texElementImage2D`) puts the stage into a WebGL texture at each exposure. Each one is
// added into a float buffer, the sum is drawn onto the canvas, and the canvas goes to the encoder as it
// is (encoder.js) — so all that goes back to Node is the encoded video. video.mjs falls back to taking
// screenshots where Chrome has no html-in-canvas.
//
// html-in-canvas is experimental (Chrome's `CanvasDrawElement`), so video.mjs checks a frame drawn here
// against a screenshot of the same moment before trusting it with a video.

"use strict";

window.gpu = (() => {
  // A triangle over the whole target; [flip] for a source stored top row first (the stage's snapshot).
  const VERTEX = `#version 300 es
    uniform bool flip;
    out vec2 uv;
    void main() {
      vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
      uv = flip ? vec2(p.x, 1.0 - p.y) : p;
      gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
    }`;
  /** One exposure, at its share of the frame, added to the sum. */
  const ADD = `#version 300 es
    precision highp float;
    in vec2 uv;
    uniform sampler2D image;
    uniform float share;
    out vec4 color;
    void main() { color = texture(image, uv) * share; }`;
  /** The sum, onto the canvas: the stage over the page's black, which is what its alpha leaves showing. */
  const SHOW = `#version 300 es
    precision highp float;
    in vec2 uv;
    uniform sampler2D image;
    out vec4 color;
    void main() { color = vec4(texture(image, uv).rgb, 1.0); }`;

  let host, gl, stage, snapshot, sum, sumBuffer, add, show;

  function program(fragment) {
    const compile = (type, source) => {
      const shader = gl.createShader(type);
      gl.shaderSource(shader, source);
      gl.compileShader(shader);
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader));
      return shader;
    };
    const p = gl.createProgram();
    gl.attachShader(p, compile(gl.VERTEX_SHADER, VERTEX));
    gl.attachShader(p, compile(gl.FRAGMENT_SHADER, fragment));
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
    return { p, flip: gl.getUniformLocation(p, "flip"), share: gl.getUniformLocation(p, "share") };
  }

  function texture() {
    const t = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, t);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.NEAREST], [gl.TEXTURE_MAG_FILTER, gl.NEAREST],
      [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
    return t;
  }

  /** Whether this Chrome can draw the page on the GPU at all: html-in-canvas, and float buffers to add into. */
  function available() {
    const probe = document.createElement("canvas").getContext("webgl2");
    return !!probe && typeof probe.texElementImage2D === "function" && !!probe.getExtension("EXT_color_buffer_float");
  }

  /**
   * Moves the stage into a canvas that lays it out without painting it, [width] × [height] device pixels,
   * and readies the buffers. From here the page is seen only through the canvas.
   */
  function setUp(width, height) {
    stage = document.querySelector("#stage");
    host = document.createElement("canvas");
    host.setAttribute("layoutsubtree", "");
    host.width = width;
    host.height = height;
    Object.assign(host.style, { position: "absolute", left: "0", top: "0", width: stage.style.width, height: stage.style.height });
    document.body.append(host);
    host.append(stage);
    gl = host.getContext("webgl2", { alpha: false, antialias: false, depth: false, preserveDrawingBuffer: false });
    if (!gl.getExtension("EXT_color_buffer_float")) throw new Error("This GPU cannot draw into float buffers");
    snapshot = texture();
    // Half floats are plenty for a sum of a dozen 8-bit exposures, and every GPU can blend them.
    sum = texture();
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA16F, width, height, 0, gl.RGBA, gl.HALF_FLOAT, null);
    sumBuffer = gl.createFramebuffer();
    gl.bindFramebuffer(gl.FRAMEBUFFER, sumBuffer);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, sum, 0);
    if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) throw new Error("The sum's buffer is incomplete");
    add = program(ADD);
    show = program(SHOW);
    gl.blendFunc(gl.ONE, gl.ONE);
  }

  /** The stage at [t], once Chrome has painted it: its snapshot is only fresh in the canvas's paint. */
  async function capture(t) {
    film.set(t);
    await new Promise((done) => {
      host.onpaint = () => done();
      host.requestPaint();
    });
    gl.bindTexture(gl.TEXTURE_2D, snapshot);
    gl.texElementImage2D(gl.TEXTURE_2D, gl.RGBA8, stage);
  }

  /** Frame [t]: its exposures added up, and the sum drawn onto the canvas. */
  async function draw(times) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, sumBuffer);
    gl.viewport(0, 0, host.width, host.height);
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT);
    for (const at of times) {
      await capture(at);
      gl.bindFramebuffer(gl.FRAMEBUFFER, sumBuffer);
      gl.enable(gl.BLEND);
      gl.useProgram(add.p);
      gl.uniform1i(add.flip, 1);
      gl.uniform1f(add.share, 1 / times.length);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    }
    gl.disable(gl.BLEND);
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, host.width, host.height);
    gl.useProgram(show.p);
    gl.uniform1i(show.flip, 0);
    gl.bindTexture(gl.TEXTURE_2D, sum);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    const error = gl.getError();
    if (error) throw new Error(`WebGL error ${error} drawing a frame`);
  }

  return {
    available,
    setUp,
    /**
     * Frames [from, to) at [fps], each blended from the exposures its motion needs (film.exposures), and
     * handed to the encoder; returns what it has finished, and how many frames were blended.
     */
    async frames(from, to, fps, scale) {
      const chunks = [];
      let blended = 0;
      for (let i = from; i < to; i++) {
        const times = film.exposures(i / fps, fps, scale);
        if (times.length > 1) blended++;
        await draw(times);
        // Taken now, before the canvas is shown and its drawing cleared.
        chunks.push(...await encoder.canvas(host, i));
      }
      return { chunks, blended };
    },
    /** Frame [t] as the video shows it, as a base64 PNG. */
    async still(t, fps, scale) {
      await draw(film.exposures(t, fps, scale));
      return host.toDataURL("image/png").split(",")[1];
    },
    /** The single moment [t], unblended, as a base64 PNG: to hold against a screenshot of it. */
    async moment(t) {
      await draw([t]);
      return host.toDataURL("image/png").split(",")[1];
    },
  };
})();
