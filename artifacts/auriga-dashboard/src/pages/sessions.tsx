import { useState } from "react";
import { useListSessions, getListSessionsQueryKey, useCreateSession } from "@workspace/api-client-react";
import { useQueryClient } from "@tanstack/react-query";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useToast } from "@/hooks/use-toast";
import { Map, CheckCircle2, XCircle } from "lucide-react";

const sessionSchema = z.object({
  deviceId: z.coerce.number().min(1, "Required"),
  locationName: z.string().min(1, "Required"),
  durationMinutes: z.coerce.number().min(1),
  hazardsDetected: z.coerce.number().optional(),
  modelLoaded: z.boolean().default(true),
  cameraWorked: z.boolean().default(true),
  audioWorked: z.boolean().default(true),
  notes: z.string().optional(),
});

export default function Sessions() {
  const [open, setOpen] = useState(false);
  const { toast } = useToast();
  const queryClient = useQueryClient();
  
  const { data: sessions, isLoading } = useListSessions({
    query: { queryKey: getListSessionsQueryKey() }
  });

  const createSession = useCreateSession({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getListSessionsQueryKey() });
        setOpen(false);
        toast({ title: "Session logged successfully" });
        form.reset();
      },
      onError: (err: any) => {
        toast({ title: "Error", description: err.message, variant: "destructive" });
      }
    }
  });

  const form = useForm<z.infer<typeof sessionSchema>>({
    resolver: zodResolver(sessionSchema),
    defaultValues: {
      deviceId: 1,
      locationName: "",
      durationMinutes: 30,
      hazardsDetected: 0,
      modelLoaded: true,
      cameraWorked: true,
      audioWorked: true,
      notes: ""
    }
  });

  const onSubmit = (values: z.infer<typeof sessionSchema>) => {
    createSession.mutate({ data: values });
  };

  const StatusIcon = ({ status }: { status?: boolean | null }) => {
    if (status === null || status === undefined) return <span className="text-muted-foreground">-</span>;
    return status ? 
      <CheckCircle2 className="h-4 w-4 text-emerald-500 inline" /> : 
      <XCircle className="h-4 w-4 text-destructive inline" />;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Field Sessions</h1>
        
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button>Log Session</Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[425px] bg-card border-border">
            <DialogHeader>
              <DialogTitle>Log Field Session</DialogTitle>
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
                    name="durationMinutes"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Duration (min)</FormLabel>
                        <FormControl>
                          <Input type="number" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
                <FormField
                  control={form.control}
                  name="locationName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Location</FormLabel>
                      <FormControl>
                        <Input placeholder="e.g. Kibera Route 1" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                
                <div className="flex gap-4 border border-border/50 p-3 rounded bg-muted/20">
                  <FormField
                    control={form.control}
                    name="modelLoaded"
                    render={({ field }) => (
                      <FormItem className="flex items-center gap-2 space-y-0">
                        <FormControl>
                          <Checkbox checked={field.value} onCheckedChange={field.onChange} />
                        </FormControl>
                        <FormLabel className="text-xs font-normal">Model</FormLabel>
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="cameraWorked"
                    render={({ field }) => (
                      <FormItem className="flex items-center gap-2 space-y-0">
                        <FormControl>
                          <Checkbox checked={field.value} onCheckedChange={field.onChange} />
                        </FormControl>
                        <FormLabel className="text-xs font-normal">Camera</FormLabel>
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="audioWorked"
                    render={({ field }) => (
                      <FormItem className="flex items-center gap-2 space-y-0">
                        <FormControl>
                          <Checkbox checked={field.value} onCheckedChange={field.onChange} />
                        </FormControl>
                        <FormLabel className="text-xs font-normal">Audio</FormLabel>
                      </FormItem>
                    )}
                  />
                </div>

                <Button type="submit" className="w-full" disabled={createSession.isPending}>
                  {createSession.isPending ? "Logging..." : "Save Session"}
                </Button>
              </form>
            </Form>
          </DialogContent>
        </Dialog>
      </div>

      <Card className="bg-card/50">
        <Table>
          <TableHeader>
            <TableRow className="border-border/50">
              <TableHead>Location</TableHead>
              <TableHead>Device</TableHead>
              <TableHead>Duration</TableHead>
              <TableHead className="text-center">Systems</TableHead>
              <TableHead className="text-right">Date</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center py-8">
                  <Skeleton className="h-4 w-32 mx-auto" />
                </TableCell>
              </TableRow>
            ) : !sessions?.length ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
                  No sessions logged
                </TableCell>
              </TableRow>
            ) : (
              sessions.map((session) => (
                <TableRow key={session.id} className="border-border/50">
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Map className="h-4 w-4 text-muted-foreground" />
                      <span className="font-medium">{session.locationName}</span>
                    </div>
                  </TableCell>
                  <TableCell className="font-mono text-sm text-muted-foreground">
                    #{session.deviceId}
                  </TableCell>
                  <TableCell>{session.durationMinutes} min</TableCell>
                  <TableCell className="text-center">
                    <div className="flex items-center justify-center gap-2">
                      <span className="text-xs text-muted-foreground" title="Model">M <StatusIcon status={session.modelLoaded} /></span>
                      <span className="text-xs text-muted-foreground" title="Camera">C <StatusIcon status={session.cameraWorked} /></span>
                      <span className="text-xs text-muted-foreground" title="Audio">A <StatusIcon status={session.audioWorked} /></span>
                    </div>
                  </TableCell>
                  <TableCell className="text-right font-mono text-sm text-muted-foreground">
                    {new Date(session.createdAt).toLocaleDateString()}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </Card>
    </div>
  );
}
