/** One immutable in-flight batch. Failed batches must be acknowledged before newer work. */
export function createProgressQueue<P, R>(options: {
  snapshot: () => P | null;
  send: (payload: P) => Promise<R>;
  acknowledge: (payload: P, result: R) => void;
}) {
  let batch: P | null = null;
  let requested = false;
  let running: Promise<void> | null = null;
  return {
    pending: () => batch,
    flush(): Promise<void> {
      requested = true;
      if (running) return running;
      running = (async () => {
        while (batch !== null || requested) {
          if (batch === null) { requested = false; batch = options.snapshot(); }
          if (batch === null) return;
          const result = await options.send(batch);
          options.acknowledge(batch, result);
          batch = null;
        }
      })().finally(() => { running = null; });
      return running;
    },
  };
}
