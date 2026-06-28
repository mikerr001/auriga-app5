import { useState } from "react";
import { useListDevices, getListDevicesQueryKey, useRegisterDevice } from "@workspace/api-client-react";
import { useQueryClient } from "@tanstack/react-query";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useToast } from "@/hooks/use-toast";
import { Smartphone, Signal, SignalZero } from "lucide-react";

const deviceSchema = z.object({
  deviceName: z.string().min(1, "Required"),
  androidVersion: z.string().min(1, "Required"),
  ramGb: z.coerce.number().min(1, "Required"),
  location: z.string().optional(),
  testerName: z.string().optional(),
});

export default function Devices() {
  const [open, setOpen] = useState(false);
  const { toast } = useToast();
  const queryClient = useQueryClient();
  
  const { data: devices, isLoading } = useListDevices({
    query: { queryKey: getListDevicesQueryKey() }
  });

  const registerDevice = useRegisterDevice({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getListDevicesQueryKey() });
        setOpen(false);
        toast({ title: "Device registered successfully" });
        form.reset();
      },
      onError: (err: any) => {
        toast({ title: "Error", description: err.message, variant: "destructive" });
      }
    }
  });

  const form = useForm<z.infer<typeof deviceSchema>>({
    resolver: zodResolver(deviceSchema),
    defaultValues: {
      deviceName: "",
      androidVersion: "",
      ramGb: 4,
      location: "",
      testerName: "",
    }
  });

  const onSubmit = (values: z.infer<typeof deviceSchema>) => {
    registerDevice.mutate({ data: values });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Device Fleet</h1>
        
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button>Register Device</Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[425px] bg-card border-border">
            <DialogHeader>
              <DialogTitle>Register New Device</DialogTitle>
            </DialogHeader>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                  control={form.control}
                  name="deviceName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Device Name</FormLabel>
                      <FormControl>
                        <Input placeholder="e.g. Pixel 6 Pro" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="androidVersion"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Android Version</FormLabel>
                        <FormControl>
                          <Input placeholder="13" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="ramGb"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>RAM (GB)</FormLabel>
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
                  name="location"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Location</FormLabel>
                      <FormControl>
                        <Input placeholder="Nairobi" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="testerName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Tester Name</FormLabel>
                      <FormControl>
                        <Input placeholder="John Doe" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <Button type="submit" className="w-full" disabled={registerDevice.isPending}>
                  {registerDevice.isPending ? "Registering..." : "Register Device"}
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
              <TableHead>Status</TableHead>
              <TableHead>Device</TableHead>
              <TableHead>Specs</TableHead>
              <TableHead>Location</TableHead>
              <TableHead>Tester</TableHead>
              <TableHead className="text-right">Last Seen</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center py-8">
                  <Skeleton className="h-4 w-32 mx-auto" />
                </TableCell>
              </TableRow>
            ) : !devices?.length ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                  No devices registered
                </TableCell>
              </TableRow>
            ) : (
              devices.map((device) => (
                <TableRow key={device.id} className="border-border/50">
                  <TableCell>
                    {device.status === 'online' ? (
                      <Signal className="h-4 w-4 text-emerald-500" />
                    ) : (
                      <SignalZero className="h-4 w-4 text-muted-foreground" />
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Smartphone className="h-4 w-4 text-muted-foreground" />
                      <span className="font-medium">{device.deviceName}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    A{device.androidVersion} &middot; {device.ramGb}GB
                  </TableCell>
                  <TableCell>{device.location || '-'}</TableCell>
                  <TableCell>{device.testerName || '-'}</TableCell>
                  <TableCell className="text-right font-mono text-sm text-muted-foreground">
                    {device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString() : 'Never'}
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
