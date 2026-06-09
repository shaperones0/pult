in vec2 vTextureCoord;
in vec4 vColor;

uniform sampler2D uTexture;
uniform float uTime;

vec3 rgb2hsv(vec3 c)
{
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c)
{
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float dots(vec2 fragCoord, float space, float gridThickness, vec2 offset)
{
    vec2 p  = fragCoord - vec2(.5) - offset;
    vec2 size = vec2(gridThickness);

    vec2 a1 = mod(p - size, space);
    vec2 a2 = mod(p + size, space);
    vec2 a = a1 - a2;

    float g = min(a.x, a.y);
    return clamp(1. - g, 0., 1.0);
}

void main(void)
{
    vec2 uv = vTextureCoord.xy;
    vec3 col = vec3(.0);

    vec4 fg = texture2D(uTexture, vTextureCoord);

    // fg.r = uvs.y + sin(uTime);
    col += 1.0 - clamp(dots(gl_FragCoord.xy, 300.0, 2.0, vec2(uTime * 10.0, uTime * 15.0)), 0.5, 1.0);
    col += 1.0 - clamp(dots(gl_FragCoord.xy, 250.0, 1.5, vec2(uTime * 8.0, uTime * 12.0)), 0.5, 1.0);
    col += 1.0 - clamp(dots(gl_FragCoord.xy, 200.0, 1.0, vec2(uTime * 6.0, uTime * 9.0)), 0.5, 1.0);
    col += 1.0 - clamp(dots(gl_FragCoord.xy, 150.0, 0.7, vec2(uTime * 5.0, uTime * 7.0)), 0.5, 1.0);
    col += 1.0 - clamp(dots(gl_FragCoord.xy, 100.0, 0.5, vec2(uTime * 4.0, uTime * 5.0)), 0.5, 1.0);
    col = clamp(col, 0.0, 1.0) + vec3(0.05);

    gl_FragColor = vec4(col, 1.0);

}
