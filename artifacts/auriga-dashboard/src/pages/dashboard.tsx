import { useGetDashboardStats, getGetDashboardStatsQueryKey } from "@workspace/api-client-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Users, Smartphone, AlertTriangle, Map } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";

export default function Dashboard() {
  const { data: stats, isLoading } = useGetDashboardStats({
    query: { queryKey: getGetDashboardStatsQueryKey() }
  });

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {[...Array(4)].map((_, i) => (
            <Skeleton key={i} className="h-32 w-full" />
          ))}
        </div>
      </div>
    );
  }

  if (!stats) return null;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Mission Control</h1>
      
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="bg-card/50">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Total Waitlist</CardTitle>
            <Users className="h-4 w-4 text-primary" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-mono font-bold">{stats.waitlistTotal.toLocaleString()}</div>
          </CardContent>
        </Card>
        
        <Card className="bg-card/50">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Devices Online</CardTitle>
            <Smartphone className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-mono font-bold">{stats.devicesTotal.toLocaleString()}</div>
          </CardContent>
        </Card>
        
        <Card className="bg-card/50">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Hazards Logged</CardTitle>
            <AlertTriangle className="h-4 w-4 text-amber-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-mono font-bold">{stats.hazardsTotal.toLocaleString()}</div>
          </CardContent>
        </Card>
        
        <Card className="bg-card/50">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Sessions Run</CardTitle>
            <Map className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-mono font-bold">{stats.sessionsTotal.toLocaleString()}</div>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="bg-card/50">
          <CardHeader>
            <CardTitle>Recent Hazards</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {stats.recentHazards.length === 0 ? (
                <p className="text-muted-foreground text-sm">No recent hazards</p>
              ) : (
                stats.recentHazards.map(hazard => (
                  <div key={hazard.id} className="flex items-center justify-between p-3 rounded bg-muted/50 border border-border/50">
                    <div>
                      <div className="font-medium text-sm">{hazard.hazardType}</div>
                      <div className="text-xs text-muted-foreground mt-1">
                        Device #{hazard.deviceId} &middot; {new Date(hazard.detectedAt).toLocaleString()}
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="font-mono text-sm text-primary">{Math.round(hazard.confidence * 100)}% conf</div>
                      <div className="text-xs text-muted-foreground mt-1">{hazard.distanceMeters ? `${hazard.distanceMeters}m` : 'N/A'}</div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>

        <Card className="bg-card/50">
          <CardHeader>
            <CardTitle>Top Hazard Types</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {stats.topHazardTypes.length === 0 ? (
                <p className="text-muted-foreground text-sm">No data available</p>
              ) : (
                stats.topHazardTypes.map(stat => (
                  <div key={stat.label} className="flex items-center justify-between">
                    <span className="text-sm font-medium">{stat.label}</span>
                    <span className="font-mono text-sm">{stat.count}</span>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
