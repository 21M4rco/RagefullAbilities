#!/usr/bin/env python3
"""Compose a contact sheet of the HexHaki effect sprites, composited over a dark ground."""
import zlib, struct, os, math

BASE = "src/main/resources/assets/hexhaki/textures/effect/"

def readpng(fn):
    d = open(fn, 'rb').read(); pos = 8; idat = b''; w = h = 0
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]; typ = d[pos+4:pos+8]; data = d[pos+8:pos+8+ln]
        if typ == b'IHDR': w, h = struct.unpack('>II', data[:8])
        if typ == b'IDAT': idat += data
        pos += 12 + ln
    raw = zlib.decompress(idat); bpp = 4; rows = []; prev = bytearray(w*bpp); i = 0
    for y in range(h):
        f = raw[i]; i += 1; line = bytearray(raw[i:i+w*bpp]); i += w*bpp
        for x in range(w*bpp):
            a = line[x-bpp] if x >= bpp else 0; b = prev[x]; c = prev[x-bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x]+a) & 255
            elif f == 2: line[x] = (line[x]+b) & 255
            elif f == 3: line[x] = (line[x]+(a+b)//2) & 255
            elif f == 4:
                p = a+b-c; pa = abs(p-a); pb = abs(p-b); pc = abs(p-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x]+pr) & 255
        rows.append(bytes(line)); prev = line
    return w, h, rows

def sample(rows, w, h, u, v):
    x = min(w-1, max(0, int(u*w))); y = min(h-1, max(0, int(v*h)))
    o = x*4
    return rows[y][o], rows[y][o+1], rows[y][o+2], rows[y][o+3]

def write_png(path, W, H, buf):
    raw = bytearray()
    for y in range(H):
        raw.append(0)
        raw += buf[y*W*3:(y+1)*W*3]
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag+data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    open(path, 'wb').write(png)

# 5x3 grid of 200px cells
NAMES = ["smoke_soft.png","glow_core.png","star.png","nebula.png","shock_ring.png",
         "crescent.png","ember.png","dust.png","crack.png","galaxy_core.png",
         "galaxy_disc.png","arc.png","arc_soft.png","soft.png","beam.png"]
CELL, COLS = 200, 5
ROWS = (len(NAMES)+COLS-1)//COLS
LABEL = 18
W, H = CELL*COLS, (CELL+LABEL)*ROWS
buf = bytearray(W*H*3)

# dark ground with a faint checker so alpha is readable
for y in range(H):
    for x in range(W):
        c = 26 if ((x//16 + y//16) % 2 == 0) else 32
        o = (y*W+x)*3
        buf[o] = c; buf[o+1] = c; buf[o+2] = c+3

# 5x7 pixel font for labels
FONT = {
 'A':["01110","10001","10001","11111","10001","10001","10001"],'B':["11110","10001","11110","10001","10001","10001","11110"],
 'C':["01111","10000","10000","10000","10000","10000","01111"],'D':["11110","10001","10001","10001","10001","10001","11110"],
 'E':["11111","10000","11110","10000","10000","10000","11111"],'G':["01111","10000","10000","10111","10001","10001","01110"],
 'H':["10001","10001","11111","10001","10001","10001","10001"],'I':["111","010","010","010","010","010","111"],
 'K':["10001","10010","11100","10100","10010","10001","10001"],'L':["10000","10000","10000","10000","10000","10000","11111"],
 'M':["10001","11011","10101","10001","10001","10001","10001"],'N':["10001","11001","10101","10011","10001","10001","10001"],
 'O':["01110","10001","10001","10001","10001","10001","01110"],'P':["11110","10001","11110","10000","10000","10000","10000"],
 'R':["11110","10001","11110","10100","10010","10001","10001"],'S':["01111","10000","01110","00001","00001","10001","01110"],
 'T':["11111","00100","00100","00100","00100","00100","00100"],'U':["10001","10001","10001","10001","10001","10001","01110"],
 'X':["10001","01010","00100","00100","00100","01010","10001"],'Y':["10001","01010","00100","00100","00100","00100","00100"],
 'F':["11111","10000","11110","10000","10000","10000","10000"],
 'W':["10001","10001","10001","10101","10101","11011","10001"],'V':["10001","10001","10001","10001","01010","01010","00100"],
 'J':["00111","00010","00010","00010","00010","10010","01100"],'Z':["11111","00001","00010","00100","01000","10000","11111"],
 'Q':["01110","10001","10001","10001","10101","10010","01101"],'_':["00000","00000","00000","00000","00000","00000","11111"],
 '(':["001","010","100","100","100","010","001"],')':["100","010","001","001","001","010","100"],
 ' ':["000","000","000","000","000","000","000"],'-':["000","000","000","1111","000","000","000"],
}
def text(px, py, s, col=(190,190,200)):
    cx = px
    for ch in s.upper():
        g = FONT.get(ch)
        if g is None: cx += 4; continue
        for gy, row in enumerate(g):
            for gx, bit in enumerate(row):
                if bit == '1':
                    X, Y = cx+gx, py+gy
                    if 0 <= X < W and 0 <= Y < H:
                        o = (Y*W+X)*3
                        buf[o], buf[o+1], buf[o+2] = col
        cx += len(g[0]) + 1

for idx, name in enumerate(NAMES):
    path = BASE + name
    if not os.path.isfile(path): continue
    w, h, rows = readpng(path)
    col = idx % COLS; row = idx // COLS
    ox, oy = col*CELL, row*(CELL+LABEL)
    legacy = name in ("soft.png", "beam.png")
    aspect = w/float(h)
    for y in range(CELL):
        for x in range(CELL):
            u = x/float(CELL); v = y/float(CELL)
            if aspect > 1.6:  # wide strips: letterbox
                vv = (v - .5) * aspect / 1.0 + .5
                if vv < 0 or vv >= 1: continue
                r, g, b, a = sample(rows, w, h, u, vv)
            else:
                r, g, b, a = sample(rows, w, h, u, v)
            o = ((oy+y)*W + ox+x)*3
            af = a/255.0
            buf[o]   = int(buf[o]*(1-af) + r*af)
            buf[o+1] = int(buf[o+1]*(1-af) + g*af)
            buf[o+2] = int(buf[o+2]*(1-af) + b*af)
    label = name.replace('.png','')
    text(ox+6, oy+CELL+5, label + (" (legacy)" if legacy else ""),
         (120,120,130) if legacy else (200,205,215))

write_png("/tmp/claude-0/-home-user-PrivateMod/801273bf-f9b3-5dd1-b81a-bb4ddec69ae8/scratchpad/hexhaki_sprites.png", W, H, buf)
print("wrote contact sheet %dx%d" % (W, H))
