import React, { useState } from "react";
import { motion } from "framer-motion";
import { Eye, Map, Zap, WifiOff, Globe, ArrowRight, ShieldCheck, Check, Github, Mail } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

export default function Home() {
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const handleSubscribe = (e: React.FormEvent) => {
    e.preventDefault();
    if (email) {
      setSubmitted(true);
    }
  };

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col selection:bg-primary selection:text-white">
      {/* Accessibility Skip Link */}
      <a href="#main-content" className="skip-link font-bold p-4">
        Skip to main content
      </a>

      {/* Navigation */}
      <nav className="border-b border-border/40 sticky top-0 bg-background/95 backdrop-blur z-50 py-4 px-6 md:px-12 flex justify-between items-center" aria-label="Main Navigation">
        <div className="flex items-center gap-3">
          <Eye className="w-8 h-8 text-accent" aria-hidden="true" />
          <span className="font-serif font-bold text-2xl tracking-wider uppercase text-foreground">
            AURIGA
          </span>
        </div>
        <div className="hidden md:flex gap-8 items-center">
          <a href="#how-it-works" className="font-medium hover:text-accent transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-4 focus-visible:ring-offset-background p-2">How It Works</a>
          <a href="#differentiators" className="font-medium hover:text-accent transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-4 focus-visible:ring-offset-background p-2">Features</a>
          <a href="#early-access" className="font-medium hover:text-accent transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-4 focus-visible:ring-offset-background p-2">Get Access</a>
        </div>
      </nav>

      <main id="main-content" className="flex-1 flex flex-col">
        {/* HERO SECTION */}
        <section className="relative w-full pt-20 pb-32 px-6 md:px-12 flex flex-col items-center justify-center overflow-hidden min-h-[90vh]" aria-labelledby="hero-heading">
          <div className="absolute inset-0 w-full h-full -z-10 bg-background">
            <img 
              src="/hero-dragon-eye.png" 
              alt="A cinematic, glowing dragon eye fused with a camera sensor, representing the powerful Auriga guardian AI." 
              className="w-full h-full object-cover opacity-30 object-center"
              role="presentation"
            />
            <div className="absolute inset-0 bg-gradient-to-b from-background/40 via-background/80 to-background" />
          </div>

          <div className="max-w-5xl mx-auto w-full z-10 grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
            <motion.div 
              initial={{ opacity: 0, y: 40 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, ease: "easeOut" }}
              className="flex flex-col gap-8"
            >
              <div className="inline-flex items-center gap-2 bg-secondary/20 border border-secondary text-secondary px-4 py-2 self-start uppercase font-bold text-sm tracking-wider">
                <ShieldCheck className="w-5 h-5" aria-hidden="true" />
                <span>The Guardian in Your Pocket</span>
              </div>
              <h1 id="hero-heading" className="text-5xl md:text-7xl font-serif font-extrabold leading-[1.1] text-foreground">
                AI that sees the world <span className="text-primary italic">for you.</span>
              </h1>
              <p className="text-xl md:text-2xl text-muted-foreground font-medium max-w-2xl leading-relaxed">
                Auriga is an offline-first spatial intelligence co-pilot for the blind and visually impaired. Precise, silent, and always watching over you—no internet required.
              </p>
              <div className="flex flex-col sm:flex-row gap-4 pt-4">
                <Button 
                  size="lg" 
                  className="bg-primary hover:bg-primary/90 text-white font-bold h-16 px-8 text-xl rounded-none border-b-4 border-r-4 border-black active:translate-y-1 active:border-b-0 active:border-r-0 transition-all focus-visible:ring-4 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-background"
                  onClick={() => document.getElementById("early-access")?.scrollIntoView({ behavior: "smooth" })}
                  aria-label="Request Early Access"
                >
                  Request Early Access <ArrowRight className="ml-2 w-6 h-6" aria-hidden="true" />
                </Button>
              </div>
            </motion.div>
          </div>
        </section>

        {/* HOW IT WORKS */}
        <section id="how-it-works" className="py-24 px-6 md:px-12 bg-card" aria-labelledby="how-it-works-heading">
          <div className="max-w-7xl mx-auto w-full">
            <div className="text-center mb-16">
              <h2 id="how-it-works-heading" className="text-4xl md:text-5xl font-serif font-bold text-foreground">
                Three Steps to <span className="text-accent">Independence</span>
              </h2>
              <p className="mt-6 text-xl text-muted-foreground max-w-3xl mx-auto">
                Auriga functions as an extension of your own senses. It operates seamlessly in real time.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-12 relative">
              {/* Decorative connecting line */}
              <div className="hidden md:block absolute top-12 left-1/6 right-1/6 h-0.5 bg-border -z-10" aria-hidden="true" />

              <motion.div 
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5 }}
                className="flex flex-col items-center text-center gap-6"
              >
                <div className="w-24 h-24 bg-background border-2 border-primary flex items-center justify-center text-primary relative">
                  <Eye className="w-10 h-10" aria-hidden="true" />
                  <div className="absolute -top-4 -left-4 w-8 h-8 bg-primary text-white font-bold flex items-center justify-center font-serif text-xl" aria-hidden="true">1</div>
                </div>
                <h3 className="text-2xl font-bold">Camera Detects</h3>
                <p className="text-lg text-muted-foreground">Your phone's camera captures the environment around you in high definition, instantly.</p>
              </motion.div>

              <motion.div 
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: 0.2 }}
                className="flex flex-col items-center text-center gap-6"
              >
                <div className="w-24 h-24 bg-background border-2 border-accent flex items-center justify-center text-accent relative">
                  <Zap className="w-10 h-10" aria-hidden="true" />
                  <div className="absolute -top-4 -left-4 w-8 h-8 bg-accent text-accent-foreground font-bold flex items-center justify-center font-serif text-xl" aria-hidden="true">2</div>
                </div>
                <h3 className="text-2xl font-bold">AI Understands</h3>
                <p className="text-lg text-muted-foreground">On-device neural networks map space, measure distance, and identify potential hazards instantly.</p>
              </motion.div>

              <motion.div 
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: 0.4 }}
                className="flex flex-col items-center text-center gap-6"
              >
                <div className="w-24 h-24 bg-background border-2 border-secondary flex items-center justify-center text-secondary relative">
                  <ShieldCheck className="w-10 h-10" aria-hidden="true" />
                  <div className="absolute -top-4 -left-4 w-8 h-8 bg-secondary text-white font-bold flex items-center justify-center font-serif text-xl" aria-hidden="true">3</div>
                </div>
                <h3 className="text-2xl font-bold">Voice Guides</h3>
                <p className="text-lg text-muted-foreground">Clear, localized audio instructions alert you to obstacles and guide you safely on your path.</p>
              </motion.div>
            </div>
          </div>
        </section>

        {/* DIFFERENTIATORS */}
        <section id="differentiators" className="py-24 px-6 md:px-12 bg-background border-y border-border" aria-labelledby="diff-heading">
          <div className="max-w-7xl mx-auto w-full grid grid-cols-1 lg:grid-cols-2 gap-16 items-center">
            <div className="order-2 lg:order-1 relative h-[400px] md:h-[600px] border border-border p-2 bg-card">
              <img 
                src="/spatial-map.png" 
                alt="A spatial map displaying radar-like visuals identifying safe paths and obstacles, highlighting Auriga's spatial memory." 
                className="w-full h-full object-cover"
              />
            </div>
            
            <div className="order-1 lg:order-2 flex flex-col gap-10">
              <div>
                <h2 id="diff-heading" className="text-4xl md:text-5xl font-serif font-bold text-foreground mb-6">
                  Not just sight. <br/><span className="text-primary">Understanding.</span>
                </h2>
                <p className="text-xl text-muted-foreground">
                  Auriga transcends basic object recognition. It builds a persistent understanding of your physical world.
                </p>
              </div>

              <ul className="flex flex-col gap-8" aria-label="Key features of Auriga">
                <li className="flex gap-4 items-start">
                  <div className="bg-primary/20 p-3 text-primary shrink-0" aria-hidden="true">
                    <WifiOff className="w-8 h-8" />
                  </div>
                  <div>
                    <h3 className="text-2xl font-bold mb-2">100% Offline-First</h3>
                    <p className="text-lg text-muted-foreground">No cloud. No loading screens. Zero latency. Auriga works deep underground or far off the grid.</p>
                  </div>
                </li>
                
                <li className="flex gap-4 items-start">
                  <div className="bg-accent/20 p-3 text-accent shrink-0" aria-hidden="true">
                    <Map className="w-8 h-8" />
                  </div>
                  <div>
                    <h3 className="text-2xl font-bold mb-2">Persistent Spatial Memory</h3>
                    <p className="text-lg text-muted-foreground">The AI remembers places you visit. It knows your home layout and flags when furniture has been moved.</p>
                  </div>
                </li>

                <li className="flex gap-4 items-start">
                  <div className="bg-secondary/20 p-3 text-secondary shrink-0" aria-hidden="true">
                    <Globe className="w-8 h-8" />
                  </div>
                  <div>
                    <h3 className="text-2xl font-bold mb-2">Built for the Global South</h3>
                    <p className="text-lg text-muted-foreground">Optimized for low-resource environments with native dialect support. Launching first in Kenya.</p>
                  </div>
                </li>
              </ul>
            </div>
          </div>
        </section>

        {/* COMPETITOR GAP */}
        <section className="py-24 px-6 md:px-12 bg-card" aria-labelledby="comparison-heading">
          <div className="max-w-5xl mx-auto w-full">
            <h2 id="comparison-heading" className="text-4xl md:text-5xl font-serif font-bold text-center mb-16">
              Why others <span className="text-destructive">fall short</span>
            </h2>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse min-w-[600px]" aria-label="Feature comparison table">
                <thead>
                  <tr className="border-b-2 border-border text-xl">
                    <th className="py-6 px-4 font-serif w-1/3">Feature</th>
                    <th className="py-6 px-4 font-serif text-muted-foreground w-1/3">Standard Apps<br/><span className="text-sm font-sans font-normal">(Be My Eyes, Envision)</span></th>
                    <th className="py-6 px-4 font-serif text-accent w-1/3 border-l border-border bg-background">Auriga</th>
                  </tr>
                </thead>
                <tbody className="text-lg">
                  <tr className="border-b border-border/50">
                    <td className="py-6 px-4 font-bold">Internet Dependency</td>
                    <td className="py-6 px-4 text-muted-foreground">Required (High latency)</td>
                    <td className="py-6 px-4 font-bold text-white border-l border-border bg-background">Completely Offline</td>
                  </tr>
                  <tr className="border-b border-border/50">
                    <td className="py-6 px-4 font-bold">Spatial Memory</td>
                    <td className="py-6 px-4 text-muted-foreground">None</td>
                    <td className="py-6 px-4 font-bold text-white border-l border-border bg-background">Remembers & Maps Spaces</td>
                  </tr>
                  <tr className="border-b border-border/50">
                    <td className="py-6 px-4 font-bold">Hazard Detection</td>
                    <td className="py-6 px-4 text-muted-foreground">Manual scanning required</td>
                    <td className="py-6 px-4 font-bold text-white border-l border-border bg-background">Real-time proactive alerts</td>
                  </tr>
                  <tr>
                    <td className="py-6 px-4 font-bold">Data Privacy</td>
                    <td className="py-6 px-4 text-muted-foreground">Images sent to cloud</td>
                    <td className="py-6 px-4 font-bold text-white border-l border-border bg-background">Never leaves your device</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        {/* CTA / EARLY ACCESS */}
        <section id="early-access" className="py-32 px-6 md:px-12 bg-primary text-primary-foreground text-center" aria-labelledby="cta-heading">
          <div className="max-w-3xl mx-auto w-full flex flex-col items-center">
            <ShieldCheck className="w-20 h-20 mb-8" aria-hidden="true" />
            <h2 id="cta-heading" className="text-5xl md:text-6xl font-serif font-extrabold mb-8 text-white">
              Claim Your Guardian
            </h2>
            <p className="text-2xl font-medium mb-12 text-primary-foreground/90 leading-relaxed">
              We are launching our private beta in Kenya. Enter your email to join the waitlist and gain early access to Auriga.
            </p>

            <form onSubmit={handleSubscribe} className="w-full max-w-xl flex flex-col sm:flex-row gap-4">
              <label htmlFor="email-input" className="sr-only">Email address</label>
              <Input 
                id="email-input"
                type="email" 
                placeholder="Enter your email address" 
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="h-16 text-xl bg-background text-foreground border-transparent focus-visible:ring-4 focus-visible:ring-accent rounded-none w-full"
                aria-required="true"
                disabled={submitted}
              />
              <Button 
                type="submit" 
                className="h-16 px-10 text-xl font-bold bg-accent hover:bg-accent/90 text-accent-foreground rounded-none shrink-0 focus-visible:ring-4 focus-visible:ring-background focus-visible:ring-offset-2 focus-visible:ring-offset-primary disabled:opacity-100 disabled:bg-secondary disabled:text-white"
                aria-label={submitted ? "Successfully joined waitlist" : "Join Waitlist"}
                disabled={submitted}
              >
                {submitted ? (
                  <span className="flex items-center gap-2"><Check className="w-6 h-6" aria-hidden="true" /> Joined</span>
                ) : "Join Waitlist"}
              </Button>
            </form>
            
            {/* Screen Reader Live Region for Form Submission */}
            <div aria-live="polite" className="sr-only">
              {submitted ? "Thank you for joining the Auriga waitlist. You will receive an email shortly." : ""}
            </div>
          </div>
        </section>
      </main>

      {/* FOOTER */}
      <footer className="bg-background py-12 px-6 md:px-12 border-t border-border" aria-labelledby="footer-heading">
        <h2 id="footer-heading" className="sr-only">Footer</h2>
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-6">
          <div className="flex items-center gap-3">
            <Eye className="w-6 h-6 text-muted-foreground" aria-hidden="true" />
            <span className="font-serif font-bold text-xl tracking-widest uppercase text-muted-foreground">
              AURIGA
            </span>
          </div>
          
          <div className="flex items-center gap-6">
            <a href="mailto:contact@auriga.ai" className="text-muted-foreground hover:text-foreground transition-colors p-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent flex items-center gap-2" aria-label="Email Auriga Support">
              <Mail className="w-6 h-6" aria-hidden="true" /> <span className="font-bold">Contact</span>
            </a>
            <a href="https://github.com" target="_blank" rel="noopener noreferrer" className="text-muted-foreground hover:text-foreground transition-colors p-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent flex items-center gap-2" aria-label="Visit Auriga GitHub Repository">
              <Github className="w-6 h-6" aria-hidden="true" /> <span className="font-bold">GitHub</span>
            </a>
          </div>
        </div>
        <div className="max-w-7xl mx-auto mt-8 text-center md:text-left">
          <p className="text-sm text-muted-foreground">© {new Date().getFullYear()} Auriga AI. All rights reserved. Built for the Global South.</p>
        </div>
      </footer>
    </div>
  );
}
