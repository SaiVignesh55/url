import React, { useRef } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import { Float, Environment } from "@react-three/drei";

const RotatingTorus = () => {
  const meshRef = useRef(null);

  useFrame(() => {
    if (!meshRef.current) return;
    meshRef.current.rotation.x += 0.003;
    meshRef.current.rotation.y += 0.004;
  });

  return (
    <Float speed={1.2} rotationIntensity={0.5} floatIntensity={1.2}>
      <mesh ref={meshRef}>
        <torusKnotGeometry args={[0.9, 0.24, 140, 18]} />
        <meshStandardMaterial color="#7c3aed" metalness={0.65} roughness={0.15} />
      </mesh>
    </Float>
  );
};

const Hero3DObject = () => {
  return (
    <div className="h-[320px] sm:h-[380px] w-full glass-card rounded-2xl overflow-hidden">
      <Canvas camera={{ position: [0, 0, 3.4], fov: 42 }}>
        <ambientLight intensity={0.7} />
        <directionalLight intensity={1.1} position={[2, 4, 3]} color="#93c5fd" />
        <directionalLight intensity={0.8} position={[-2, -2, 1]} color="#c4b5fd" />
        <RotatingTorus />
        <Environment preset="city" />
      </Canvas>
    </div>
  );
};

export default Hero3DObject;

