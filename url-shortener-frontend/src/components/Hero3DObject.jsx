import React, { Suspense, useEffect, useRef } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import { Environment } from "@react-three/drei";
import { useLocation } from "react-router-dom";

const RotatingTorus = () => {
  const meshRef = useRef(null);

  useFrame(() => {
    if (!meshRef.current) return;
    meshRef.current.rotation.y += 0.0025;
  });

  return (
    <group position={[1.4, 0, 0]} scale={2.1}>
      <mesh ref={meshRef}>
        <torusKnotGeometry args={[1.15, 0.38, 200, 32]} />
        <meshStandardMaterial color="#7c3aed" metalness={0.9} roughness={0.14} />
      </mesh>
    </group>
  );
};

const Hero3DObject = ({ className = "h-[320px] sm:h-[380px] w-full" }) => {
  const location = useLocation();

  useEffect(() => {
    if (import.meta.env.DEV) {
      console.debug("[Hero3DObject] mounted");
    }
  }, []);

  useEffect(() => {
    if (import.meta.env.DEV) {
      console.debug("[Hero3DObject] route changed:", location.pathname);
    }
  }, [location.pathname]);

  return (
    <div className={`${className} min-h-[260px]`} style={{ width: "100%", height: "100%" }}>
      <Canvas
        key={location.pathname}
        camera={{ position: [1.8, 0, 6.2], fov: 50 }}
        frameloop="always"
        dpr={[1, 1.5]}
        style={{ width: "100%", height: "100%", display: "block" }}
      >
        <Suspense fallback={null}>
          <ambientLight intensity={0.45} />
          <directionalLight position={[3, 3, 5]} intensity={1.2} />
          <pointLight position={[2, 0, 3]} intensity={2} color="#a855f7" />
          <pointLight position={[-2, 0, 2]} intensity={1} color="#6366f1" />
          <RotatingTorus />
          <Environment preset="city" />
        </Suspense>
      </Canvas>
    </div>
  );
};

export default Hero3DObject;

