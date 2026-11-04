// hls.js ships types for the full build only; the light build has the same API (no subtitles/alt-audio/EME).
declare module 'hls.js/dist/hls.light.mjs' {
  import Hls from 'hls.js';
  export default Hls;
}
