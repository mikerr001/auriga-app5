import { useState } from "react";
import { useListHazards, getListHazardsQueryKey, useGetHazardSummary, getGetHazardSummaryQueryKey, useReportHazard } from "@workspace/api-client-react";
import { useQueryClient } from "@tanstack/react-query";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useToast } from "@/hooks/use-toast";
import { AlertTriangle, Volume2, Vibrate } from "lucide-react";

const hazardSchema = z.object({
  deviceId: z.coerce.number().min(1, "Required"),
  hazardType: z.string().min(1, "Required"),
  confidence: z.coerce.number().min(0).max(1),
  distanceMeters: z.coerce.number().optional(),
  bearingDegrees: z.coerce.number().optional(),
  audioAlertFired: z.boolean().default(false),
  hapticAlertFired: z.boolean().default(false),
  notes: z.string().optional(),
});

export default function Hazards() {
  const [deviceIdFilter, setDeviceIdFilter] = useState("");
  const [open, setOpen] = useState(false);
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const parsedDeviceId = deviceIdFilter ? parseInt(deviceIdFilter) : undefined;
  
  const { data: hazards, isLoading: isLoadingList } = useListHazards(
    parsedDeviceId ? { deviceId: parsedDeviceId } : {},
    { query: { queryKey: getListHazardsQueryKey(parsedDeviceId ? { deviceId: parsedDeviceId } : {}) } }
  );

  const { data: summary, isLoading: isLoadingSummary } = useGetHazardSummary({
    query: { queryKey: getGetHazardSummaryQueryKey() }
  });

  const reportHazard = useReportHazard({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getListHazardsQueryKey() });
        queryClient.invalidateQueries({ queryKey: getGetHazardSummaryQueryKey() });
        setOpen(false);
        toast({ title: "Test hazard logged" });
        form.reset();
      },
      onError: (err: any) => {
        toast({ title: "Error", description: err.message, variant: "destructive" });
      }
    }
  });

  const form = useForm<z.infer<typeof hazardSchema>>({
    resolver: zodResolver(hazardSchema),
    defaultValues: {
      deviceId: 1,
      hazardType: "Pothole",
      confidence: 0.95,
      distanceMeters: 2.5,
      bearingDegrees: 15,
      audioAlertFired: true,
      hapticAlertFired: true,
      notes: "Test event"
    }
  });

  const onSubmit = (values: z.infer<typeof hazardSchema>) => {
    reportHazard.mutate({ data: values });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Hazard Feed</h1>
        
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button variant="outline" className="border-primary text-primary hover:bg-primary/10">Log Test Event</Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[425px] bg-card border-border">
            <DialogHeader>
              <DialogTitle>Log Test Hazard</DialogTitle>
            </DialogHeader>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="deviceId"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Device ID</FormLabel>
                        <FormControl>
                          <Input type="number" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="confidence"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Confidence (0-1)</FormLabel>
                        <FormControl>
                          <Input type="number" step="0.01" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <FormField
                  control={form.control}
                  name="hazardType"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Hazard Type</FormLabel>
                      <Select onValueChange={field.onChange} defaultValue={field.value}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="Select type" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="Pothole">Pothole</SelectItem>
                          <SelectItem value="Step down">Step down</SelectItem>
                          <SelectItem value="Low branch">Low branch</SelectItem>
                          <SelectItem value="Vehicle">Vehicle</SelectItem>
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <Button type="submit" className="w-full" disabled={reportHazard.isPending}>
                  {reportHazard.isPending ? "Logging..." : "Log Event"}
                </Button>
              </form>
            </Form>
          </DialogContent>
        </Dialog>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="bg-card/50 md:col-span-1">
          <CardHeader>
            <CardTitle>Distribution</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoadingSummary ? <Skeleton className="h-32 w-full" /> : (
              <div className="space-y-3">
                <div className="flex justify-between items-center text-sm border-b border-border/50 pb-2">
                  <span className="text-muted-foreground">Total Events</span>
                  <span className="font-mono font-bold text-primary">{summary?.totalEvents.toLocaleString()}</span>
                </div>
                <div className="flex justify-between items-center text-sm border-b border-border/50 pb-2">
                  <span className="text-muted-foreground">Avg Confidence</span>
                  <span className="font-mono font-medium">{summary ? Math.round(summary.avgConfidence * 100) : 0}%</span>
                </div>
                <div className="pt-2 space-y-2">
                  {summary?.byType.map(s => (
                    <div key={s.label} className="flex justify-between items-center text-sm">
                      <span>{s.label}</span>
                      <span className="font-mono">{s.count}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="bg-card/50 md:col-span-2 flex flex-col">
          <CardHeader className="flex flex-row items-center justify-between pb-4">
            <CardTitle>Event Log</CardTitle>
            <Input 
              placeholder="Filter by Device ID..." 
              value={deviceIdFilter}
              onChange={(e) => setDeviceIdFilter(e.target.value)}
              className="max-w-[200px] h-8"
            />
          </CardHeader>
          <CardContent className="flex-1 overflow-auto p-0">
            <Table>
              <TableHeader>
                <TableRow className="border-border/50">
                  <TableHead className="pl-4">Type</TableHead>
                  <TableHead>Conf</TableHead>
                  <TableHead>Dist</TableHead>
                  <TableHead>Device</TableHead>
                  <TableHead>Alerts</TableHead>
                  <TableHead className="text-right pr-4">Time</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoadingList ? (
                  <TableRow>
                    <TableCell colSpan={6} className="text-center py-8">
                      <Skeleton className="h-4 w-32 mx-auto" />
                    </TableCell>
                  </TableRow>
                ) : !hazards?.length ? (
                  <TableRow>
                    <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                      No hazards recorded
                    </TableCell>
                  </TableRow>
                ) : (
                  hazards.map((hazard) => (
                    <TableRow key={hazard.id} className="border-border/50">
                      <TableCell className="pl-4 font-medium flex items-center gap-2">
                        <AlertTriangle className="h-3.5 w-3.5 text-amber-500" />
                        {hazard.hazardType}
                      </TableCell>
                      <TableCell className="font-mono text-sm text-emerald-500">
                        {Math.round(hazard.confidence * 100)}%
                      </TableCell>
                      <TableCell className="font-mono text-sm">
                        {hazard.distanceMeters ? `${hazard.distanceMeters}m` : '-'}
                      </TableCell>
                      <TableCell className="font-mono text-sm text-muted-foreground">
                        #{hazard.deviceId}
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-2">
                          <Volume2 className={`h-4 w-4 ${hazard.audioAlertFired ? 'text-primary' : 'text-muted-foreground/30'}`} />
                          <Vibrate className={`h-4 w-4 ${hazard.hapticAlertFired ? 'text-primary' : 'text-muted-foreground/30'}`} />
                        </div>
                      </TableCell>
                      <TableCell className="text-right pr-4 font-mono text-xs text-muted-foreground">
                        {new Date(hazard.detectedAt).toLocaleTimeString()}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
