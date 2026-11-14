const savers = new Set<() => Promise<void>>();

/** Includes directory/header controls outside the active player component. */
export function registerProgressSaver(save: () => Promise<void>) {
  savers.add(save);
  return () => { savers.delete(save); };
}

export async function flushActiveProgress() {
  await Promise.all([...savers].map(save => save()));
}
