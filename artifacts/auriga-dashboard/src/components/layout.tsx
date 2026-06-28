import { Link, useLocation } from "wouter";
import {
  Sidebar,
  SidebarContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
  SidebarGroup,
  SidebarGroupContent,
} from "@/components/ui/sidebar";
import { Activity, Users, Smartphone, AlertTriangle, Map } from "lucide-react";

export default function Layout({ children }: { children: React.ReactNode }) {
  const [location] = useLocation();

  const navItems = [
    { href: "/", label: "Overview", icon: Activity },
    { href: "/waitlist", label: "Waitlist", icon: Users },
    { href: "/devices", label: "Fleet", icon: Smartphone },
    { href: "/hazards", label: "Hazards", icon: AlertTriangle },
    { href: "/sessions", label: "Sessions", icon: Map },
  ];

  return (
    <div className="dark min-h-screen bg-background text-foreground">
      <SidebarProvider>
        <Sidebar className="border-r border-border bg-card">
          <SidebarHeader className="p-4">
            <div className="flex items-center gap-2 font-bold text-lg text-primary tracking-tight">
              <div className="w-6 h-6 rounded bg-primary flex items-center justify-center">
                <span className="text-primary-foreground text-xs font-mono">A</span>
              </div>
              AURIGA OPS
            </div>
          </SidebarHeader>
          <SidebarContent>
            <SidebarGroup>
              <SidebarGroupContent>
                <SidebarMenu>
                  {navItems.map((item) => {
                    const isActive = location === item.href;
                    return (
                      <SidebarMenuItem key={item.href}>
                        <SidebarMenuButton asChild isActive={isActive}>
                          <Link href={item.href} className="flex items-center gap-3">
                            <item.icon className="h-4 w-4" />
                            <span>{item.label}</span>
                          </Link>
                        </SidebarMenuButton>
                      </SidebarMenuItem>
                    );
                  })}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          </SidebarContent>
        </Sidebar>
        <div className="flex-1 flex flex-col min-h-screen overflow-hidden">
          <header className="h-14 border-b border-border flex items-center px-4 shrink-0 bg-card/50 backdrop-blur">
            <SidebarTrigger />
            <div className="ml-auto flex items-center gap-2">
              <div className="flex items-center gap-2 text-xs font-mono text-emerald-500 bg-emerald-500/10 px-2 py-1 rounded border border-emerald-500/20">
                <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                SYSTEM NOMINAL
              </div>
            </div>
          </header>
          <main className="flex-1 overflow-auto p-6">
            <div className="max-w-7xl mx-auto w-full">
              {children}
            </div>
          </main>
        </div>
      </SidebarProvider>
    </div>
  );
}
