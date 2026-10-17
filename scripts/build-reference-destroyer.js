/* Run inside an empty Generic Model through the Blockbench MCP risky_eval tool. */
(() => {
    if (Outliner.elements.length) throw new Error('Use an empty project; existing work is preserved.');
    const made = [], textures = [], groups = [];
    Undo.initEdit({elements: made, textures, groups, outliner: true, uv_mode: true});
    Project.texture_width = Project.texture_height = 256;
    Project.name = 'SES Legislator of Supremacy';
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 256;
    const ctx = canvas.getContext('2d');
    const texture = new Texture({name: 'destroyer_materials.png', render_sides: 'double'}).add(false);
    textures.push(texture);
    const colors = {armor: '#666b75', pale: '#828893', panel: '#5b626d', edge: '#939aa3',
        dark: '#303640', black: '#171d25', steel: '#515966', yellow: '#b6b75b',
        trim: '#969950', pipe: '#706d63', cyan: '#82dbed', white: '#d7f3ed',
        red: '#d97761', glass: '#547b89'};
    const slots = new Map();
    const indices = {north:[0,3,2,1], south:[4,5,6,7], down:[0,1,5,4],
        up:[3,7,6,2], west:[0,4,7,3], east:[1,2,6,5]};
    const cross = (a,b) => [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
    const sub = (a,b) => a.map((n,i)=>n-b[i]);
    function uv(mat, p) {
        const n=cross(sub(p[1],p[0]),sub(p[3],p[0])), len=Math.hypot(...n);
        if (len < 1e-8) throw new Error('Degenerate face');
        const glow=['cyan','white','red'].includes(mat);
        const light=glow ? 1 : Math.round((0.65+0.34*Math.max(0,(-0.3*n[0]+0.86*n[1]+0.4*n[2])/len))*32)/32;
        const rgb=colors[mat].slice(1).match(/../g).map(v=>Math.round(parseInt(v,16)*light));
        const alpha=mat==='glass'?80:255;
        const key=[...rgb,alpha].join(',');
        if (!slots.has(key)) {
            const i=slots.size; slots.set(key,i);
            ctx.fillStyle=`rgba(${rgb.join(',')},${alpha/255})`;
            ctx.fillRect(i%128*2,Math.floor(i/128)*2,2,2);
        }
        const i=slots.get(key),x=i%128*2,y=Math.floor(i/128)*2;
        return [x+.25,y+.25,x+1.75,y+1.75];
    }
    const group = name => { const g=new Group({name,origin:[0,0,0]}).init(); groups.push(g); return g; };
    function solid(g,name,p,mat='armor',bounds) {
        const tag=['cyan','white','red'].includes(mat)?'emit__':mat==='glass'?'glass__':'';
        let e;
        if (bounds) {
            e=new Cube({name:tag+name,from:bounds[0],to:bounds[1],origin:[0,0,0],autouv:0,box_uv:false,shade:false});
            for (const [f,ii] of Object.entries(indices)) e.faces[f].extend({uv:uv(mat,ii.map(i=>p[i])),texture:texture.uuid});
        } else {
            e=new Mesh({name:tag+name,vertices:{},faces:{},origin:[0,0,0]});
            const keys=e.addVertices(...p);
            const center=[0,1,2].map(k=>p.reduce((a,v)=>a+v[k],0)/p.length);
            for (const [f,raw] of Object.entries(indices)) {
                const pts=raw.map(i=>p[i]), n=cross(sub(pts[1],pts[0]),sub(pts[3],pts[0]));
                const outward=[0,1,2].map(k=>pts.reduce((a,v)=>a+v[k],0)/4-center[k]);
                const ii=n.reduce((a,v,k)=>a+v*outward[k],0)<0?[...raw].reverse():raw;
                const r=uv(mat,ii.map(i=>p[i])), coords=[[r[0],r[1]],[r[2],r[1]],[r[2],r[3]],[r[0],r[3]]];
                e.faces[f]=new MeshFace(e,{vertices:ii.map(i=>keys[i]),texture:texture.uuid,
                    uv:Object.fromEntries(ii.map((v,i)=>[keys[v],coords[i]]))});
            }
        }
        e.addTo(g).init(); made.push(e); return e;
    }
    function box(g,n,a,b,m='armor') {
        if (a.some((v,i)=>v>=b[i])) throw new Error('Invalid box '+n);
        const [x,y,z]=a,[X,Y,Z]=b;
        return solid(g,n,[[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]],m,[a,b]);
    }
    function taper(g,n,x,X,a,b,m='armor') {
        const [y,Y,z,Z]=a,[v,V,w,W]=b;
        return solid(g,n,[[x,y,z],[X,v,w],[X,V,w],[x,Y,z],[x,y,Z],[X,v,W],[X,V,W],[x,Y,Z]],m);
    }
    function beam(g,n,a,b,w,d,m='edge') {
        const delta=sub(b,a),len=Math.hypot(...delta);
        let u=Math.abs(delta[1])<len*.98?[-delta[2],0,delta[0]]:[1,0,0];
        const ul=Math.hypot(...u); u=u.map(v=>v/ul);
        let v=cross(delta,u); const vl=Math.hypot(...v); v=v.map(t=>t/vl);
        const pts=[[a,-1,-1],[b,-1,-1],[b,-1,1],[a,-1,1],[a,1,-1],[b,1,-1],[b,1,1],[a,1,1]];
        return solid(g,n,pts.map(([p,s,t])=>p.map((q,i)=>q+u[i]*s*w/2+v[i]*t*d/2)),m);
    }
    function ring(g,n,x,X,y,z,r,inner,m='edge',axis='x') {
        const point=(d,a,rad)=>axis==='x'?[d,y+Math.cos(a)*rad,z+Math.sin(a)*rad]:[x+Math.cos(a)*rad,y+Math.sin(a)*rad,d];
        const start=axis==='x'?x:z,end=axis==='x'?X:z+(X-x);
        for(let i=0;i<12;i++) {
            const a=i*Math.PI/6,b=(i+1)*Math.PI/6;
            solid(g,n+'_'+i,[point(start,a,inner),point(end,a,inner),point(end,a,r),point(start,a,r),
                point(start,b,inner),point(end,b,inner),point(end,b,r),point(start,b,r)],m);
        }
    }
    function panel(g,n,x,X,y,z,Z,m='armor') {
        box(g,n,[x,y,z],[X,y+1.8,Z],m);
        for (const a of [x+2,X-3]) for(const b of [z+2,Z-3]) box(g,n+'_lock_'+a+'_'+b,[a,y+1.8,b],[a+1.2,y+2.2,b+1.2],'steel');
    }
    const hull=group('01_aft_hull_and_stepped_shoulders');
    taper(hull,'aft_pressure_hull',-279,-211,[-17,18,-52,52],[-27,21,-62,62],'dark');
    taper(hull,'mid_pressure_hull',-211,-82,[-27,21,-62,62],[-19,18,-55,55],'dark');
    taper(hull,'forward_root',-82,-20,[-19,0,-55,55],[-13,-5,-58,58],'black');
    for(const s of [-1,1]) {
        const z=s*57;
        taper(hull,'shoulder_inset_'+s,-221,-92,[-15,17,z-25,z+25],[-13,14,z-22,z+22],'black');
        for(let i=0;i<4;i++) {
            const x=-219+i*31;
            taper(hull,'shoulder_armor_'+s+'_'+i,x,x+29,[9,24,z-24,z+24],[6,21,z-23,z+23],i%3===0?'pale':'armor');
            box(hull,'shoulder_side_'+s+'_'+i,[x,-11,s*80-1.5],[x+29,8,s*80+1.5],'armor');
            box(hull,'shoulder_inset_panel_'+s+'_'+i,[x+4,-8,s*82-0.6],[x+25,4,s*82+.6],'black');
            box(hull,'shoulder_inner_panel_'+s+'_'+i,[x+6,-6,s*82.8-.3],[x+23,2,s*82.8+.3],'steel');
            beam(hull,'shoulder_frame_'+s+'_'+i,[x+2,-11,s*83],[x+2,8,s*83],1.6,1.6,'edge');
        }
        box(hull,'shoulder_yellow_end_'+s,[-97,-11,s*82-1],[-94,13,s*82+1],'yellow');
        panel(hull,'aft_deck_'+s,-221,-170,22,s<0?-46:4,s<0?-4:46,'panel');
        panel(hull,'mid_deck_'+s,-168,-109,22,s<0?-46:4,s<0?-4:46);
        for(let i=0;i<8;i++) box(hull,'deck_vent_'+s+'_'+i,[-207+i*4,24,s*31-6],[-205+i*4,24.5,s*31+6],'black');
    }
    const bow=group('02_twin_parallel_bow_girders');
    for(const s of [-1,1]) {
        const z=s*44;
        taper(bow,'long_structural_girder_'+s,-92,356,[-13,-5,z-24,z+24],[-10,-5,z-24,z+24],'black');
        box(bow,'raised_inboard_web_'+s,[-92,-5,s<0?-44:20],[356,20,s<0?-20:44],'dark');
        taper(bow,'bow_lower_frame_'+s,356,378,[-10,-7,z-24,z+24],[-6,-3,z-20,z+20],'steel');
        for(const edge of [-1,1]) {
            const a=z+edge*23,b=z+edge*19;
            taper(bow,'bow_side_frame_'+s+'_'+edge,356,378,[-9,12,a-2,a+2],[-5,10,b-2,b+2],'armor');
            box(bow,'bow_sensor_probe_'+s+'_'+edge,[365,-3,z+edge*10-.65],[393, -1.7,z+edge*10+.65],'steel');
            box(bow,'bow_sensor_tip_'+s+'_'+edge,[390,-3,z+edge*10-.75],[395,-1.5,z+edge*10+.75],'edge');
        }
        for(let i=0;i<12;i++) {
            const x=-88+i*37;
            const outside=s*68, inside=s*20;
            panel(bow,'deck_laminate_'+s+'_'+i,x,x+35,20,s<0?-44:21,s<0?-21:44,i%4===0?'panel':'armor');
            box(bow,'lower_plate_'+s+'_'+i,[x,-14,outside-2],[x+34,-7,outside+2],'panel');
            beam(bow,'lower_rib_'+s+'_'+i,[x,-15,outside],[x+6,-3,outside],1.8,2.2,'edge');
            box(bow,'inner_mechanical_socket_'+s+'_'+i,[x+2,-9,inside-2],[x+16,9,inside+2],'steel');
            beam(bow,'inner_truss_'+s+'_'+i,[x+3,-7,inside-s*1.5],[x+29,8,inside-s*1.5],1.2,1.2,'edge');
            box(bow,'upper_rail_post_'+s+'_'+i,[x+4,22,s*44-1],[x+5.6,29,s*44+1],'edge');
            box(bow,'outer_service_clip_'+s+'_'+i,[x+9,-4,outside-3.2],[x+12,-.8,outside+3.2],'pale');
            box(bow,'ventral_girder_plate_'+s+'_'+i,[x+2,-14,z-18],[x+33,-13,z+18],'panel');
            beam(bow,'ventral_diagonal_'+s+'_'+i,[x+3,-14.4,z-17],[x+31,-14.4,z+17],1.3,1.2,'steel');
            box(bow,'ventral_pipe_clip_'+s+'_'+i,[x+15,-17,z-1.7],[x+18,-14,z+1.7],'edge');
            if(i%2===0) box(bow,'running_lamp_'+s+'_'+i,[x+19,-6,outside-3.3],[x+25,-5.3,outside+3.3],'white');
        }
        for(let i=0;i<5;i++) {
            const x=-90+i*89,X=x+87;
            solid(bow,'sloped_broadside_'+s+'_'+i,[[x,20,s*45],[X,20,s*45],[X,-2,s*71],[x,-2,s*71],
                [x,18,s*44],[X,18,s*44],[X,-4,s*70],[x,-4,s*70]],i===4?'panel':'armor');
            beam(bow,'broadside_lower_edge_'+s+'_'+i,[x,-2,s*71],[X,-2,s*71],1,1,'edge');
            for(const xx of [x+3,X-3]) {
                beam(bow,'broadside_seam_'+s+'_'+i+'_'+xx,[xx,19.7,s*46],[xx,-.8,s*70],.55,.55,'dark');
                for(const zz of [47,68]) {
                    const y=20-(zz-45)*22/26;
                    beam(bow,'flush_fastener_'+s+'_'+i+'_'+xx+'_'+zz,[xx,y+.3,s*zz],[xx+1.3,y+.3,s*zz],.8,.8,'edge');
                }
            }
        }
        for(const r of [24,44]) {
            box(bow,'continuous_top_rail_'+s+'_'+r,[-78,29,s*r-1],[359,31,s*r+1],'steel');
            box(bow,'top_rail_highlight_'+s+'_'+r,[-78,31,s*r-.55],[359,31.5,s*r+.55],'edge');
        }
        box(bow,'inner_bronze_conduit_'+s,[-51,-4,s*19-0.65],[355,-2.7,s*19+.65],'pipe');
        box(bow,'inner_upper_conduit_'+s,[-51,7,s*19-0.6],[353,8,s*19+.6],'steel');
        box(bow,'ventral_service_main_'+s,[-84,-16.8,z-.8],[355,-15.2,z+.8],'pipe');
        for(let i=0;i<6;i++) {
            const x=-54+i*65;
            box(bow,'dorsal_equipment_well_'+s+'_'+i,[x,22,s*33-5],[x+42,22.5,s*33+5],'black');
            box(bow,'dorsal_equipment_'+s+'_'+i,[x+3,22.5,s*33-3],[x+37,24,s*33+3],'steel');
        }
        taper(bow,'bow_cap_'+s,357,378,[21,23,z-25,z+25],[9,11,z-20,z+20],'panel');
        box(bow,'bow_terminal_lamp_'+s,[377,-3,z-6],[378,0,z-2],'white');
        beam(bow,'terminal_antenna_'+s,[359,22,s*57],[359,41,s*57],2,2,'steel');
        box(bow,'terminal_antenna_head_'+s,[357,35,s*57-1.5],[361,43,s*57+1.5],'pale');
    }
    const spine=group('03_recessed_center_channel');
    taper(spine,'recessed_channel_floor',-145,293,[-17,-10,-16,16],[-12,-7,-16,16],'dark');
    for(let i=0;i<15;i++) {
        const x=-119+i*27;
        box(spine,'channel_floor_panel_'+i,[x,-9,-13],[x+24,-7.8,13],'steel');
        box(spine,'channel_center_recess_'+i,[x+2,-7.7,-5],[x+22,-7.3,5],'black');
        for(const s of [-1,1]) box(spine,'channel_pipe_clip_'+i+'_'+s,[x+4,-7,s*10-.7],[x+6,-4,s*10+.7],'edge');
    }
    for(const s of [-1,1]) box(spine,'channel_long_pipe_'+s,[-134,-5,s*10-.65],[295,-3.7,s*10+.65],'pipe');
    taper(spine,'channel_front_machinery',292,338,[-12,-6,-11,11],[-8,-3,-8,8],'black');
    const fins=group('04_swept_stern_stabilizers');
    for(const s of [-1,1]) for(const t of [-1,1]) {
        const n=s+'_'+t;
        const ry=t>0?30:-10,ty=t>0?12:-63;
        const root=[-289,ry,s*51], tip=[-302,ty,s*110];
        const frontRoot=[-231,ry+3,s*53], frontTip=[-214,ty,s*110];
        const off=[0,t*3,-s*1.5];
        solid(fins,'raked_fin_'+n,[root,frontRoot,frontTip,tip,...[root,frontRoot,frontTip,tip].map(p=>p.map((v,i)=>v+off[i]))],'armor');
        beam(fins,'fin_outer_edge_'+n,tip,frontTip,2.3,2.3,'edge');
        beam(fins,'fin_identification_'+n,[-231,ry+(ty-ry)*.13+t*3.2,s*59],[-216,ty+t*3.2,s*107],3,1.2,'yellow');
        beam(fins,'fin_root_spar_'+n,root,frontRoot,5,5,'dark');
        for(let j=0;j<3;j++) {
            const f=.26+j*.22, z=s*(51+59*f),y=ry+(ty-ry)*f;
            beam(fins,'fin_panel_joint_'+n+'_'+j,[-289-13*f,y+t*3.1,z-s*1.5],[-233+17*f,y+t*3.1,z-s*1.5],.65,.65,'dark');
        }
        beam(fins,'fin_actuator_'+n,[-253,t*4,s*51],[-241,ry+(ty-ry)*.44-t*3,s*77],3,3,'steel');
        beam(fins,'fin_actuator_rod_'+n,[-241,ry+(ty-ry)*.44-t*3,s*77],[-230,ry+(ty-ry)*.76-t*3,s*96],1.5,1.5,'edge');
        for(let j=0;j<3;j++) {
            const f=.35+j*.15,z=s*(51+59*f),y=ry+(ty-ry)*f+t*3.5;
            beam(fins,'fin_inset_panel_'+n+'_'+j,[-280,y,z],[-246,y,z],6,.4,'panel');
            beam(fins,'fin_panel_latch_'+n+'_'+j,[-281,y+t*.4,z],[-279,y+t*.4,z],1,.5,'edge');
        }
    }
    const engines=group('05_stern_propulsion_and_rcs');
    taper(engines,'stern_drive_block',-304,-269,[-17,17,-50,50],[-20,20,-52,52],'steel');
    for(const z of [-33,-11,11,33]) {
        box(engines,'main_engine_recess_'+z,[-309,-12,z-9],[-304,12,z+9],'black');
        for(const y of [-11,11]) box(engines,'engine_lip_'+z+'_'+y,[-311,y-1.5,z-10],[-305,y+1.5,z+10],'edge');
        for(const side of [-1,1]) box(engines,'engine_vertical_lip_'+z+'_'+side,[-311,-10,z+side*9-1],[-305,10,z+side*9+1],'armor');
        box(engines,'drive_throat_'+z,[-309.2,-6,z-6],[-308.9,6,z+6],'steel');
        box(engines,'drive_core_'+z,[-309.5,-1.1,z-4],[-309.3,1.1,z+4],'cyan');
    }
    for(const s of [-1,1]) for(const x of [-259,-105]) for(const y of [-13,13]) {
        const z=s*(x===-259?56:85),n=x+'_'+s+'_'+y;
        ring(engines,'rcs_mount_'+n,x,x+2.2,y,z,6,4.5,'armor','z');
        ring(engines,'rcs_lip_'+n,x,x+1.4,y,z+s*.8,4.2,3.1,'edge','z');
        ring(engines,'rcs_emission_'+n,x,x+.5,y,z+s*1.8,3,1.9,'cyan','z');
        ring(engines,'rcs_center_'+n,x,x+.55,y,z+s*1.9,1.85,.1,'black','z');
    }
    const keel=group('06_armored_ventral_keel');
    taper(keel,'aft_belly',-241,-169,[-22,-12,-42,42],[-53,-19,-35,35],'dark');
    taper(keel,'deep_reactor_hull',-169,-80,[-53,-19,-35,35],[-49,-17,-33,33],'steel');
    taper(keel,'forward_belly_ramp',-80,18,[-49,-17,-33,33],[-19,-12,-21,21],'dark');
    for(const s of [-1,1]) {
        for(let i=0;i<5;i++) {
            const x=-199+i*29, y=i<2?-40:-48+(i-2)*9;
            beam(keel,'heavy_belly_frame_'+s+'_'+i,[x,-20,s*48],[x+10,y,s*31],6,5,'armor');
            beam(keel,'belly_frame_highlight_'+s+'_'+i,[x+2,-21,s*49],[x+12,y,s*32],1.2,1.2,'edge');
            box(keel,'belly_service_plate_'+s+'_'+i,[x+11,y,s*34-2],[x+28,y+12,s*34+2],'panel');
        }
        beam(keel,'aft_reactor_feed_'+s,[-226,-28,s*28],[-167,-51,s*28],3,3,'pipe');
        beam(keel,'reactor_lower_rail_'+s,[-170,-54,s*27],[-82,-50,s*27],3,3,'edge');
    }
    for(let i=0;i<5;i++) panel(keel,'ventral_access_'+i,-166+i*17,-151+i*17,-55,-22,22,'armor');
    const weapons=group('07_ventral_defense_batteries');
    for(const [x,y,z] of [[-126,-60,0],[-48,-33,-35],[-48,-33,35],[29,-20,-33],[29,-20,33]]) {
        const n=x+'_'+z;
        taper(weapons,'turret_mount_'+n,x-9,x+9,[y,y+8,z-10,z+10],[y,y+8,z-10,z+10],'dark');
        taper(weapons,'turret_casing_'+n,x-9,x+10,[y-7,y,z-8,z+8],[y-4,y+1,z-6,z+6],'armor');
        for(const b of [-3,3]) {
            beam(weapons,'twin_cannon_'+n+'_'+b,[x+4,y-4,z+b],[x+26,y-13,z+b],2,2,'edge');
            beam(weapons,'barrel_muzzle_'+n+'_'+b,[x+23,y-11.8,z+b],[x+28,y-14,z+b],3,3,'dark');
        }
    }
    const bridge=group('08_low_command_deck_and_sensors');
    taper(bridge,'low_command_house',-231,-167,[23,32,-19,19],[23,29,-17,17],'dark');
    taper(bridge,'command_roof',-235,-161,[32,35,-22,22],[29,32,-20,20],'armor');
    for(const s of [-1,1]) for(let i=0;i<6;i++) {
        const x=-228+i*9;
        box(bridge,'bridge_window_'+s+'_'+i,[x,25,s*19-.3],[x+6,30,s*19+.3],'glass');
        box(bridge,'window_mullion_'+s+'_'+i,[x-1,24,s*19-.5],[x,31,s*19+.5],'steel');
    }
    for(let i=0;i<3;i++) {
        const x=-234+i*11,z=(i-1)*10;
        box(bridge,'mast_base_'+i,[x-2,33,z-2],[x+2,38,z+2],'steel');
        beam(bridge,'communications_mast_'+i,[x,37,z],[x,55-i*5,z],.8,.8,'edge');
        beam(bridge,'mast_crossbar_'+i,[x-4,47-i*4,z],[x+4,47-i*4,z],.7,.7,'steel');
    }
    const details=group('09_radiators_piping_and_hull_fittings');
    for(const s of [-1,1]) {
        for(let i=0;i<18;i++) {
            const x=-76+i*24,z=66+(i%3)*.6,y=20-(z-45)*22/26;
            beam(details,'broadside_surface_wear_'+s+'_'+i,[x,y+.22,s*z],[x+2+i%5,y+.22,s*z],.22,.18,i%2?'edge':'panel');
            if(i%3===0) {
                const zz=48,yy=20-(zz-45)*22/26;
                beam(details,'small_service_stencil_'+s+'_'+i,[x,yy+.25,s*zz],[x+5,yy+.25,s*zz],.35,.2,'edge');
            }
        }
        box(details,'shoulder_cooling_well_'+s,[-123,-11,s*85-1],[-102,9,s*85+1],'black');
        for(let i=0;i<9;i++) box(details,'shoulder_cooling_louver_'+s+'_'+i,[-122,-10+i*2,s*86-.7],[-103,-9.3+i*2,s*86+.7],'edge');
        for(let i=0;i<12;i++) {
            const x=-84+i*36;
            box(details,'maintenance_hatch_'+s+'_'+i,[x+8,22,s*36-4],[x+20,23.1,s*36+4],'panel');
            box(details,'hatch_handle_'+s+'_'+i,[x+12,23.2,s*36-.5],[x+16,23.7,s*36+.5],'edge');
        }
        for(let i=0;i<5;i++) {
            const x=-273+i*12;
            box(details,'aft_cooling_rib_'+s+'_'+i,[x,20,s*37-5],[x+2,23,s*37+5],'edge');
        }
        for(let i=0;i<6;i++) {
            const x=-212+i*18,z=s*60;
            box(details,'shoulder_recess_'+s+'_'+i,[x,24,z-7],[x+14,24.3,z+7],'dark');
            for(let j=0;j<4;j++) box(details,'shoulder_grille_'+s+'_'+i+'_'+j,[x+1+j*3,24.3,z-6],[x+1.8+j*3,24.8,z+6],'steel');
            box(details,'shoulder_access_tab_'+s+'_'+i,[x+5,24.9,z+8],[x+8,25.5,z+10],'edge');
        }
        for(let i=0;i<9;i++) {
            const x=-220+i*14;
            beam(details,'shoulder_inner_feed_'+s+'_'+i,[x,25,s*48],[x+5,25,s*38],1.1,1,'steel');
        }
        for(let i=0;i<8;i++) {
            const x=-215+i*13;
            box(details,'keel_vent_'+s+'_'+i,[x,-23,s*43-.5],[x+7,-20,s*43+.5],'black');
            box(details,'keel_warning_tab_'+s+'_'+i,[x,-24,s*44-.3],[x+2,-22,s*44+.3],'trim');
        }
        beam(details,'aft_red_running_light_'+s,[-236,24,s*44],[-223,24,s*44],1,1,'red');
    }
    const markings=group('10_yellow_registry_and_recognition_stripes');
    // Geometry lettering survives the untextured SOD1 renderer without a decal shader.
    const letters=document.createElement('canvas'); letters.width=1024; letters.height=64;
    const lc=letters.getContext('2d'); lc.font='italic 26px Arial'; lc.fillStyle='white';
    lc.fillText('SES Legislator of Supremacy',2,31);
    const im=lc.getImageData(0,0,450,40),scale=.43;
    for(const s of [-1,1]) {
        for(let row=0;row<40;row++) {
            let start=-1;
            for(let col=0;col<=450;col++) {
                const on=col<450&&im.data[(row*450+col)*4+3]>135;
                if(on&&start<0) start=col;
                if(!on&&start>=0) {
                    const a=s>0?start:450-col,b=s>0?col:450-start;
                    const z=48+row*.41,y=20-(z-45)*22/26;
                    beam(markings,'registry_'+s+'_'+row+'_'+start,[-34+a*scale,y+.4,s*z],
                        [-34+b*scale,y+.4,s*z],.37,.22,'yellow');
                    start=-1;
                }
            }
        }
        for(let i=0;i<3;i++) {
            const z=54+i*3.1,y=20-(z-45)*22/26;
            beam(markings,'long_recognition_bar_'+s+'_'+i,[158,y+.3,s*z],[241-i*6,y+.3,s*z],1.7,.24,'yellow');
        }
        for(let i=0;i<3;i++) box(markings,'aft_warning_band_'+s+'_'+i,[-166+i*4,24.05,s*57-12],[-164+i*4,24.3,s*57+12],'trim');
    }
    texture.fromDataURL(canvas.toDataURL('image/png'));
    texture.uv_width=texture.uv_height=256;
    Undo.finishEdit('Build reference twin-boom SES destroyer');
    Canvas.updateAll();
    return {elements:made.length,groups:Group.all.length,palette:slots.size,bounds:'bow +X, dorsal +Y',vertices:made.length*24};
})()
