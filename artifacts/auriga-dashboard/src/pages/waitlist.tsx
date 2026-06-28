import { useListWaitlist, getListWaitlistQueryKey, useGetWaitlistStats, getGetWaitlistStatsQueryKey } from "@workspace/api-client-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export default function Waitlist() {
  const { data: waitlist, isLoading: isLoadingList } = useListWaitlist({
    query: { queryKey: getListWaitlistQueryKey() }
  });
  
  const { data: stats, isLoading: isLoadingStats } = useGetWaitlistStats({
    query: { queryKey: getGetWaitlistStatsQueryKey() }
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Waitlist Directory</h1>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card className="bg-card/50">
          <CardHeader>
            <CardTitle>By Country</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoadingStats ? <Skeleton className="h-32 w-full" /> : (
              <div className="space-y-2">
                {stats?.byCountry.map(c => (
                  <div key={c.label} className="flex justify-between items-center text-sm">
                    <span className="text-muted-foreground">{c.label || 'Unknown'}</span>
                    <span className="font-mono font-medium">{c.count}</span>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
        
        <Card className="bg-card/50">
          <CardHeader>
            <CardTitle>By Source</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoadingStats ? <Skeleton className="h-32 w-full" /> : (
              <div className="space-y-2">
                {stats?.bySource.map(s => (
                  <div key={s.label} className="flex justify-between items-center text-sm">
                    <span className="text-muted-foreground">{s.label || 'Direct'}</span>
                    <span className="font-mono font-medium">{s.count}</span>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <Card className="bg-card/50">
        <Table>
          <TableHeader>
            <TableRow className="border-border/50">
              <TableHead>Email</TableHead>
              <TableHead>Name</TableHead>
              <TableHead>Country</TableHead>
              <TableHead>Source</TableHead>
              <TableHead className="text-right">Joined</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoadingList ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center py-8">
                  <Skeleton className="h-4 w-32 mx-auto" />
                </TableCell>
              </TableRow>
            ) : !waitlist?.length ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
                  Waitlist is empty
                </TableCell>
              </TableRow>
            ) : (
              waitlist.map((entry) => (
                <TableRow key={entry.id} className="border-border/50">
                  <TableCell className="font-medium">{entry.email}</TableCell>
                  <TableCell>{entry.name || '-'}</TableCell>
                  <TableCell>{entry.country || '-'}</TableCell>
                  <TableCell>{entry.source || '-'}</TableCell>
                  <TableCell className="text-right font-mono text-sm text-muted-foreground">
                    {new Date(entry.createdAt).toLocaleDateString()}
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
