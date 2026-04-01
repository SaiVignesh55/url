import { forwardRef } from "react";
import { useGLTF } from "@react-three/drei";

const Model = forwardRef(function Model(props, ref) {
  const { scene } = useGLTF("/models/model.glb");

  return <primitive ref={ref} object={scene} {...props} />;
});

useGLTF.preload("/models/model.glb");

export default Model;

