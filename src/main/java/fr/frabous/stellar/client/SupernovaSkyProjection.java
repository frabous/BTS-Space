package fr.frabous.stellar.client;

/** A finite celestial proxy: preserve every viewing ray without moving the viewer or loading terrain. */
final class SupernovaSkyProjection {
    static final double MAX_CENTER_DISTANCE = 1100;
    static final double MAX_EXTENT = 2700;
    final double x, y, z, scale, distance;

    private SupernovaSkyProjection(double x, double y, double z, double scale, double distance) {
        this.x=x;this.y=y;this.z=z;this.scale=scale;this.distance=distance;
    }

    static SupernovaSkyProjection project(double sourceX, double sourceY, double sourceZ,
                                          double eyeX, double eyeY, double eyeZ, double radius) {
        double dx=sourceX-eyeX,dy=sourceY-eyeY,dz=sourceZ-eyeZ;
        double distance=Math.hypot(Math.hypot(dx,dz),dy);
        double scale=Math.min(1,MAX_EXTENT/Math.max(1,distance+Math.max(0,radius)));
        if (distance>MAX_CENTER_DISTANCE) scale=Math.min(scale,MAX_CENTER_DISTANCE/distance);
        return new SupernovaSkyProjection(eyeX+dx*scale,eyeY+dy*scale,eyeZ+dz*scale,scale,distance);
    }

    /** Invert the actual affine view, including native F5 offsets, camera bob and portal distortion. */
    static double[] eye(float[] m) {
        double a=m[0],b=m[4],c=m[8],d=m[1],e=m[5],f=m[9],g=m[2],h=m[6],i=m[10];
        double determinant=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if (!Double.isFinite(determinant) || Math.abs(determinant)<1e-12) return null;
        double x=m[12],y=m[13],z=m[14];
        double[] eye={-((e*i-f*h)*x+(c*h-b*i)*y+(b*f-c*e)*z)/determinant,
                -((f*g-d*i)*x+(a*i-c*g)*y+(c*d-a*f)*z)/determinant,
                -((d*h-e*g)*x+(b*g-a*h)*y+(a*e-b*d)*z)/determinant};
        for (double value:eye) if (!Double.isFinite(value)) return null;
        return eye;
    }
}
