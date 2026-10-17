"""Verify editable/runtime geometry parity and preview the autonomous capital ship."""
import argparse
import base64
import io
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FACES = {"north": (0,3,2,1), "south": (4,5,6,7), "down": (0,1,5,4),
         "up": (3,7,6,2), "west": (0,4,7,3), "east": (1,2,6,5)}


def validate():
    model = json.loads((ROOT / "models/orbital_destroyer.bbmodel").read_text(encoding="utf-8"))
    data = (ROOT / "src/main/resources/assets/stellar/models/orbital_destroyer.mesh").read_bytes()
    magic, count = struct.unpack_from(">II", data)
    assert magic == 0x534F4431 and len(data) == 8+count*17
    mesh = np.frombuffer(data, dtype=[("pos",">f4",(3,)),("rgb",">u4"),("tag","u1")], offset=8).copy()
    assert np.isfinite(mesh["pos"]).all() and set(mesh["tag"]) == {0,1,2}
    exported = {e["uuid"] for e in model["elements"] if e.get("export", True)}
    assert count == len(exported)*24
    elements = {e["uuid"]: e for e in model["elements"]}
    assert len(elements) == len(model["elements"])
    texture = Image.open(io.BytesIO(base64.b64decode(model["textures"][0]["source"].split(",")[1])))
    assert texture.size == (model["resolution"]["width"],model["resolution"]["height"])
    groups = {g["uuid"]: g for g in model.get("groups", [])}
    ordered = [(elements[child],groups.get(g["uuid"],g).get("name", ""))
               for g in model["outliner"] if groups.get(g["uuid"],g).get("export", True) for child in g["children"]]
    assert len(ordered) == len(exported)
    max_error = 0
    for index,(e,group) in enumerate(ordered):
        if e["type"] == "cube":
            lo,hi = e["from"],e["to"]
            assert all(hi[i] > lo[i] for i in range(3)),e["name"]
            corners = [(lo[0],lo[1],lo[2]),(hi[0],lo[1],lo[2]),(hi[0],hi[1],lo[2]),(lo[0],hi[1],lo[2]),
                       (lo[0],lo[1],hi[2]),(hi[0],lo[1],hi[2]),(hi[0],hi[1],hi[2]),(lo[0],hi[1],hi[2])]
        for f,(name,indices) in enumerate(FACES.items()):
            face = mesh[index*24+f*4:index*24+f*4+4]
            source = e["faces"][name]
            if e["type"] == "cube":
                points = np.array([corners[i] for i in indices])
                u1,v1,u2,v2 = source["uv"]
            else:
                points = np.array([e["vertices"][v] for v in source["vertices"]])
                uv = list(source["uv"].values())
                u1,v1,u2,v2 = *uv[0],*uv[2]
            max_error = max(max_error,float(np.max(np.abs(face["pos"]-points))))
            r,g,b,a = texture.getpixel((int((u1+u2)/2),int((v1+v2)/2)))
            assert (face["rgb"] == r<<16|g<<8|b).all(),e["name"]
            expected_tag = 1 if e["name"].startswith("emit__") else 2 if e["name"].startswith("glass__") else 0
            assert (face["tag"] == expected_tag).all(),e["name"]
            if expected_tag == 2:
                assert a == 80 and (face["tag"] == 2).all(),e["name"]
            u,v = points[1]-points[0],points[3]-points[0]
            assert np.linalg.norm(np.cross(u,v)) > 1e-8,e["name"]
    assert max_error < 0.00003,max_error
    assert len(elements) == len(set(elements))
    assert len({g["uuid"] for g in model["outliner"]}) == len(model["outliner"])
    names = {e["name"] for e in model["elements"]}
    assert {"long_structural_girder_-1","long_structural_girder_1"} <= names
    assert not any("central_prow" in n or "hangar" in n for n in names)
    assert sum(n.startswith("raked_fin_") for n in names) == 4
    assert sum(n.startswith("registry_") for n in names) > 100
    assert count <= 100000
    bounds = [mesh["pos"].min(axis=0).tolist(),mesh["pos"].max(axis=0).tolist()]
    assert bounds[1][0]-bounds[0][0] > 167*3
    assert bounds[1][2]-bounds[0][2] > 68*3
    print(json.dumps({"editable_runtime_parity": "PASS", "twin_bow_and_stern_fins": "PASS",
                      "capital_ship_scale": "PASS", "elements": len(elements),"quads": count//4,
                      "max_float_error": max_error,"bounds": bounds},indent=2))
    return mesh


def render(mesh,camera,title,size=(900,620),subtitle="SES Legislator of Supremacy | geometry preview | no Minecraft bloom"):
    width,height = size
    points = mesh["pos"].astype(float)
    view = np.array(camera,dtype=float)
    view /= np.linalg.norm(view)
    right = np.cross([0,1,0],view)
    right /= np.linalg.norm(right)
    up = np.cross(view,right)
    projected = points @ np.stack((right,up,view),axis=1)
    scale = min((width-90)/np.ptp(projected[:,0]),(height-110)/np.ptp(projected[:,1]))
    center = (projected[:,:2].min(axis=0)+projected[:,:2].max(axis=0))/2
    xy = (projected[:,:2]-center)*[scale,-scale]+[width/2,height/2+20]
    depth = np.full((height,width),-np.inf)
    pixels = np.zeros((height,width,3),dtype=np.uint8)
    pixels[:] = [13,17,26]
    opaque = [q for q in range(0,len(mesh),4) if int(mesh["tag"][q]) != 2]
    glass = sorted((q for q in range(0,len(mesh),4) if int(mesh["tag"][q]) == 2),
                   key=lambda q: projected[q:q+4,2].mean())
    for q in opaque+glass:
        rgb = int(mesh["rgb"][q])
        color = [rgb>>16&255,rgb>>8&255,rgb&255]
        for indices in ([q,q+1,q+2],[q,q+2,q+3]):
            p = xy[indices]
            x0,y0 = np.maximum(0,np.floor(p.min(axis=0)).astype(int))
            x1,y1 = np.minimum([width-1,height-1],np.ceil(p.max(axis=0)).astype(int))
            if x1 < x0 or y1 < y0:
                continue
            den = (p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
            if abs(den) < 1e-8:
                continue
            yy,xx = np.mgrid[y0:y1+1,x0:x1+1]
            a = ((p[1,1]-p[2,1])*(xx-p[2,0])+(p[2,0]-p[1,0])*(yy-p[2,1]))/den
            b = ((p[2,1]-p[0,1])*(xx-p[2,0])+(p[0,0]-p[2,0])*(yy-p[2,1]))/den
            c = 1-a-b
            z = a*projected[indices[0],2]+b*projected[indices[1],2]+c*projected[indices[2],2]
            block = depth[y0:y1+1,x0:x1+1]
            mask = (a >= 0)&(b >= 0)&(c >= 0)&(z >= block)
            canvas = pixels[y0:y1+1,x0:x1+1]
            if int(mesh["tag"][q]) == 2:
                alpha = 80 / 255.0
                canvas[mask] = (canvas[mask].astype(float)*(1-alpha)+np.asarray(color)*alpha).astype(np.uint8)
            else:
                canvas[mask] = color
            if int(mesh["tag"][q]) != 2:
                block[mask] = z[mask]
    image = Image.fromarray(pixels)
    draw = ImageDraw.Draw(image)
    try:
        title_font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",max(16,width//55))
        small_font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",max(12,width//85))
    except OSError:
        title_font=small_font=ImageFont.load_default()
    draw.text((22,18),title,fill="#e0e6ee",font=title_font)
    draw.text((22,height-28),subtitle,fill="#8090a8",font=small_font)
    return image


def select_parts(mesh, groups, exclude=(), guide=False):
    model=json.loads((ROOT / "models/orbital_destroyer.bbmodel").read_text(encoding="utf-8"))
    elements={e["uuid"]: e for e in model["elements"]}
    chunks=[]
    index=0
    for group in model["outliner"]:
        if not group.get("export",True):
            continue
        for child in group["children"]:
            name=elements[child]["name"]
            if group["name"] in groups and not any(word in name for word in exclude):
                chunks.append(mesh[index*24:(index+1)*24])
            index+=1
    if guide:
        texture=Image.open(io.BytesIO(base64.b64decode(model["textures"][0]["source"].split(",")[1])))
        group=next(g for g in model["outliner"] if g["name"]=="player_scale_guide_hidden")
        for child in group["children"]:
            e=elements[child]; lo,hi=e["from"],e["to"]
            corners=[(lo[0],lo[1],lo[2]),(hi[0],lo[1],lo[2]),(hi[0],hi[1],lo[2]),(lo[0],hi[1],lo[2]),
                     (lo[0],lo[1],hi[2]),(hi[0],lo[1],hi[2]),(hi[0],hi[1],hi[2]),(lo[0],hi[1],hi[2])]
            data=[]
            for name,indices in FACES.items():
                u1,v1,u2,v2=e["faces"][name]["uv"]
                r,g,b,_=texture.getpixel((int((u1+u2)/2),int((v1+v2)/2)))
                data.extend((corners[i],r<<16|g<<8|b,0) for i in indices)
            chunks.append(np.array(data,dtype=mesh.dtype))
    return np.concatenate(chunks)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output",type=Path)
    args = parser.parse_args()
    mesh = validate()
    if args.output:
        assert args.output.parent.is_dir(),"Output parent must already exist"
        sheet = Image.new("RGB",(1800,1240))
        shots = [((80,150,290),"DORSAL / TWIN PARALLEL BOW"),
                 ((-190,75,160),"STERN / SWEPT STABILIZERS"),
                 ((160,-125,190),"VENTRAL / SEALED CAPITAL-SHIP KEEL"),
                 ((0,90,220),"BROADSIDE / COMMAND BRIDGE AND KEEL")]
        for i,(camera,title) in enumerate(shots):
            sheet.paste(render(mesh,camera,title),((i%2)*900,(i//2)*620))
        sheet.save(args.output)
        print("Saved "+str(args.output))
        hero=render(mesh,(80,150,290),"SES LEGISLATOR OF SUPREMACY",(1600,900),
                    "Reference reconstruction | native Blockbench MCP geometry | runtime mesh preview")
        shots={"hero":hero,
               "stern":render(mesh,(-195,80,155),"STERN / RECESSED DRIVES AND STABILIZERS",(1600,1000)),
               "ventral":render(mesh,(90,-130,260),"VENTRAL / KEEL AND DEFENSE BATTERIES",(1600,900)),
               "bow":render(mesh,(600,95,40),"BOW / OPEN FRAME ENDS AND CENTER CHANNEL",(1400,1000))}
        for name,shot in shots.items():
            output=args.output.with_name(args.output.stem+"-"+name+".jpg")
            shot.resize((980,round(shot.height*980/shot.width)),Image.Resampling.LANCZOS).save(output,quality=87,optimize=True)
            print("Saved "+str(output)+" ("+str(output.stat().st_size)+" bytes)")


if __name__ == "__main__":
    main()
