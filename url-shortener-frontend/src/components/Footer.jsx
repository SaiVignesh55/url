import React from "react";
import { FaFacebook, FaTwitter, FaInstagram, FaLinkedin } from "react-icons/fa";
import { motion as Motion } from "framer-motion";

const Footer = () => {
  return (
    <Motion.footer
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      className="py-8 z-40 relative"
    >
      <div className="container mx-auto px-6 lg:px-14 flex flex-col lg:flex-row lg:justify-between items-center gap-4 glass-card p-6">
        <div className="text-center lg:text-left">
          <h2 className="text-3xl font-bold mb-2">Linklytics</h2>
          <p className="text-slate-200">Simplifying URL shortening for efficient sharing</p>
        </div>

        <p className="mt-4 lg:mt-0 text-slate-200">
          &copy; 2024 Linklytics. All rights reserved.
        </p>

        <div className="flex space-x-6 mt-4 lg:mt-0">
          <a href="#" className="hover:text-blue-300 transition-colors duration-200">
            <FaFacebook size={24} />
          </a>
          <a href="#" className="hover:text-blue-300 transition-colors duration-200">
            <FaTwitter size={24} />
          </a>
          <a href="#" className="hover:text-blue-300 transition-colors duration-200">
            <FaInstagram size={24} />
          </a>
          <a href="#" className="hover:text-blue-300 transition-colors duration-200">
            <FaLinkedin size={24} />
          </a>
        </div>
      </div>
    </Motion.footer>
  );
};

export default Footer;