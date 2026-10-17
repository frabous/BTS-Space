/* Native Blockbench MCP builder. Model units are 1/16 block. */
(() => {
    if (Outliner.elements.length) throw new Error('Create an empty Generic Model first');
    const elements=[],groups=[],textures=[];
    Undo.initEdit({elements,groups,textures,outliner:true,uv_mode:true});
    Project.name='AG-03 Compact Graviton Cannon';
    Project.texture_width=Project.texture_height=64;
    const canvas=document.createElement('canvas');canvas.width=canvas.height=64;
    const ctx=canvas.getContext('2d'),palette={armor:'#666b75',pale:'#858d96',edge:'#a1a8ad',dark:'#303640',black:'#171d25',yellow:'#b6b75b',cyan:'#82dbed',white:'#def4ef'};
    const texture=new Texture({name:'graviton_palette.png',render_sides:'double'}).add(false);textures.push(texture);
    const swatches=new Map(),faces={north:[0,3,2,1],south:[4,5,6,7],down:[0,1,5,4],up:[3,7,6,2],west:[0,4,7,3],east:[1,2,6,5]};
    function group(name){const g=new Group({name}).init();groups.push(g);return g;}
    function solid(g,name,p,mat='armor') {
        const glow=['cyan','white'].includes(mat),e=new Mesh({name:(glow?'emit__':'')+name,vertices:{},faces:{},origin:[0,0,0]});
        const keys=e.addVertices(...p);
        for(const [f,ids] of Object.entries(faces)) {
            const a=p[ids[0]],b=p[ids[1]],d=p[ids[3]],u=b.map((v,i)=>v-a[i]),v=d.map((v,i)=>v-a[i]);
            const n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]],len=Math.hypot(...n);
            const light=glow?1:.65+.33*Math.max(0,(-.3*n[0]+.86*n[1]+.4*n[2])/len);
            const rgb=palette[mat].slice(1).match(/../g).map(c=>Math.round(parseInt(c,16)*light)),key=rgb.join(',');
            if(!swatches.has(key)){const i=swatches.size;swatches.set(key,i);ctx.fillStyle=`rgb(${key})`;ctx.fillRect(i%32*2,Math.floor(i/32)*2,2,2);}
            const i=swatches.get(key),x=i%32*2,y=Math.floor(i/32)*2,uv=[[x+.25,y+.25],[x+1.75,y+.25],[x+1.75,y+1.75],[x+.25,y+1.75]];
            e.faces[f]=new MeshFace(e,{vertices:ids.map(i=>keys[i]),texture:texture.uuid,uv:Object.fromEntries(ids.map((j,k)=>[keys[j],uv[k]]))});
        }
        e.addTo(g).init();elements.push(e);
    }
    function taper(g,n,x,X,a,b,m='armor') {
        const [y,Y,z,Z]=a,[v,V,w,W]=b;
        solid(g,n,[[x,y,z],[X,v,w],[X,V,w],[x,Y,z],[x,y,Z],[X,v,W],[X,V,W],[x,Y,Z]],m);
    }
    function box(g,n,a,b,m='armor'){taper(g,n,a[0],b[0],[a[1],b[1],a[2],b[2]],[a[1],b[1],a[2],b[2]],m);}
    const base=group('base');
    taper(base,'low_armored_chassis',-30,29,[3,18,-22,22],[3,14,-19,19],'dark');
    taper(base,'chassis_top',-26,24,[18,24,-20,20],[14,23,-18,18]);
    box(base,'turntable_socket',[-13,23,-13],[13,28,13],'black');
    box(base,'yaw_bearing',[-11,28,-11],[11,32,11],'edge');
    for(const s of [-1,1]) {
        box(base,'cradle_'+s,[-10,30,s*15-2],[10,48,s*15+2],'armor');
        box(base,'axle_'+s,[-5,43,s*17-2],[5,53,s*17+2],'edge');
        box(base,'axle_inset_'+s,[-3,45,s*19-.3],[3,51,s*19+.3],'black');
        box(base,'chassis_stripe_'+s,[-24,12,s*22-.3],[19,14,s*22+.3],'yellow');
        for(let i=0;i<8;i++) box(base,'chassis_louver_'+s+'_'+i,[-23+i*5,5,s*22-.4],[-21+i*5,10,s*22+.4],'pale');
        box(base,'status_light_'+s,[-19,19,s*18-.5],[-9,20,s*18+.5],'cyan');
    }
    for(let i=0;i<4;i++) {
        const sx=i<2?-1:1,sz=i%2?-1:1,g=group('leg_'+i),x=sx*28,z=sz*27;
        taper(g,'outrigger_'+i,x-9,x+9,[5,14,z-6,z+6],[3,9,z-6,z+6],'dark');
        box(g,'hydraulic_piston_'+i,[x-7,10,z-2],[x+7,12,z+2],'edge');
        box(g,'arm_cover_'+i,[x-6,12,z-4],[x+6,15,z+4],'armor');
        box(g,'landing_pad_'+i,[x-10,0,z-9],[x+10,3,z+9],'dark');
        box(g,'pad_tread_'+i,[x-9,3,z-8],[x+9,4,z+8],'pale');
        box(g,'pad_warning_'+i,[x-9,4,z-7],[x+9,4.4,z-5],'yellow');
        for(let j=0;j<4;j++) box(g,'pad_grip_'+i+'_'+j,[x-7+j*4,4,z-3],[x-6+j*4,4.5,z+6],'black');
    }
    const barrel=group('barrel');
    taper(barrel,'breech',-34,10,[36,60,-12,12],[38,58,-11,11],'dark');
    taper(barrel,'sloped_receiver_top',-32,12,[60,64,-12,12],[58,61,-11,11],'pale');
    box(barrel,'rear_reactor_frame',[-36,39,-10],[-34,57,10],'edge');
    box(barrel,'rear_reactor_well',[-36.4,41,-8],[-36.1,55,8],'black');
    box(barrel,'rear_reactor_core',[-36.6,46,-5],[-36.5,50,5],'cyan');
    for(const s of [-1,1]) {
        taper(barrel,'breech_cheek_'+s,-31,9,[38,59,s*13-3,s*13+3],[39,57,s*13-3,s*13+3]);
        for(let i=0;i<7;i++)box(barrel,'receiver_grille_'+s+'_'+i,[-28+i*5,41,s*16-.4],[-25+i*5,54,s*16+.4],'black');
        box(barrel,'receiver_ident_'+s,[-27,55,s*16-.5],[5,56.8,s*16+.5],'yellow');
        taper(barrel,'parallel_rail_'+s,10,93,[39,57,s*10-4,s*10+4],[41,55,s*10-3,s*10+3],'dark');
        for(let j=0;j<5;j++) {
            const x=12+j*16;
            taper(barrel,'rail_segment_'+s+'_'+j,x,x+14,[40,59,s*12-3,s*12+3],[41,57,s*12-3,s*12+3],j%2?'armor':'pale');
            box(barrel,'rail_bolt_'+s+'_'+j,[x+3,58,s*12-.7],[x+5,59,s*12+.7],'edge');
            box(barrel,'coil_'+s+'_'+j,[x+2,46,s*6-.5],[x+13,50,s*6+.5],'cyan');
        }
        box(barrel,'barrel_ident_'+s,[39,53,s*15-.3],[70,54,s*15+.3],'yellow');
        box(barrel,'muzzle_side_'+s,[88,40,s*12-3],[96,56,s*12+3],'edge');
        box(barrel,'muzzle_inner_'+s,[89,40,s*9-.5],[96,56,s*9+.5],'black');
        box(barrel,'muzzle_light_'+s,[96,44,s*10-.6],[96.2,52,s*10+.6],'cyan');
    }
    for(const y of [38,56])box(barrel,'muzzle_crossbar_'+y,[88,y,-12],[96,y+2,12],'armor');
    for(let j=0;j<4;j++)box(barrel,'bore_lower_crossbrace_'+j,[15+j*22,39,-9],[18+j*22,41,9],'dark');
    box(barrel,'bore_recess',[10,43,-5],[11,53,5],'black');
    box(barrel,'bore_core',[11.1,46,-2],[11.2,50,2],'white');
    texture.fromDataURL(canvas.toDataURL('image/png'));texture.uv_width=texture.uv_height=64;
    Undo.finishEdit('Build compact ship-matched graviton cannon');Canvas.updateAll();
    return {elements:elements.length,groups:groups.length,muzzle:[96,48,0],pivot:[0,48,0],scale:1/16};
})()
