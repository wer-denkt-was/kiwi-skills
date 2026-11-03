<script setup lang="ts">
import { onMounted } from 'vue'
import { useApiErrorHandler, useApiService, useLoading } from '@werdenktwas/kiwi-vue/services'
import type { ITodoItemDefault, TodoListId } from '@/api/demo.model'
import { TodoItemService } from '@/services/todo.services'
import NewTodoItem from './NewTodoItem.vue'
import TodoItemEditor from './TodoItemEditor.vue'

const props = defineProps<{ listId: TodoListId }>()

const errorHandler = useApiErrorHandler()
const itemService = errorHandler.wrapService(useApiService(TodoItemService))
const items = useLoading<ITodoItemDefault[]>()

async function refresh() {
  await items.load(
    itemService
      .findPage({
        filter: { listId: props.listId },
        sort: [{ field: 'order', direction: 'asc' }],
      })
      .then(page => [...page.items]),
  )
}

onMounted(refresh)
</script>

<template>
  <Loading :state="items">
    <template #pending>Loading…</template>
    <template #loaded="{ data }">
      <TodoItemEditor v-for="item in data" :key="item.id" :item="item" @updated="refresh" />
    </template>
    <template #failed>Could not load the items.</template>
  </Loading>
  <NewTodoItem :list-id="listId" @created="refresh" />
</template>
