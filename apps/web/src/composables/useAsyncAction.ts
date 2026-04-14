import { computed, ref } from "vue";

export function useAsyncAction() {
  const pendingKeys = ref(new Set<string>());
  const anyPending = computed(() => pendingKeys.value.size > 0);

  function isPending(key: string) {
    return pendingKeys.value.has(key);
  }

  async function run<T>(key: string, action: () => Promise<T>): Promise<T | undefined> {
    if (pendingKeys.value.has(key)) {
      return undefined;
    }
    const next = new Set(pendingKeys.value);
    next.add(key);
    pendingKeys.value = next;
    try {
      return await action();
    } finally {
      const remaining = new Set(pendingKeys.value);
      remaining.delete(key);
      pendingKeys.value = remaining;
    }
  }

  return {
    anyPending,
    isPending,
    run
  };
}
