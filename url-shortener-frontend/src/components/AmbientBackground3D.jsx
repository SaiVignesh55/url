import React, { useMemo, useRef } from "react";
import { Canvas, useFrame } from "@react-three/fiber";

const FloatingParticles = () => {
  const pointsRef = useRef(null);

  const positions = useMemo(() => {
    const count = 120;
    const values = new Float32Array(count * 3);
    for (let i = 0; i < count; i += 1) {
      const seed = i * 12.9898;
      values[i * 3] = Math.sin(seed) * 7;
      values[i * 3 + 1] = Math.cos(seed * 0.8) * 4.5;
      values[i * 3 + 2] = Math.sin(seed * 0.6) * 3.2;
    }
    return values;
  }, []);

  useFrame(({ clock }) => {
    if (!pointsRef.current) return;
    pointsRef.current.rotation.y = clock.getElapsedTime() * 0.015;
    pointsRef.current.rotation.x = Math.sin(clock.getElapsedTime() * 0.07) * 0.05;
  });

  return (
    <points ref={pointsRef}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" count={positions.length / 3} array={positions} itemSize={3} />
      </bufferGeometry>
      <pointsMaterial size={0.08} color="#8b5cf6" transparent opacity={0.25} sizeAttenuation />
    </points>
  );
};

const AmbientBackground3D = () => {
  return (
    <div className="fixed inset-0 -z-10 pointer-events-none opacity-90">
      <Canvas camera={{ position: [0, 0, 8], fov: 55 }} dpr={[1, 1.5]}>
        <ambientLight intensity={0.45} />
        <directionalLight intensity={0.35} position={[2, 4, 2]} color="#60a5fa" />
        <FloatingParticles />
      </Canvas>
    </div>
  );
};

export default AmbientBackground3D;

