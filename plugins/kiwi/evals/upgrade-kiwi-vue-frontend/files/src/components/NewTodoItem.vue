<script setup lang="ts">
import { ref } from 'vue'
import { useApiErrorHandler, useApiService } from '@werdenktwas/kiwi-vue/services'
import type { ICreateTodoItem, ITodoItemDefault, TodoListId } from '@/api/demo.model'
import { TodoItemService } from '@/services/todo.services'

const props = defineProps<{ listId: TodoListId }>()
const emit = defineEmits<{ created: [item: ITodoItemDefault] }>()

const errorHandler = useApiErrorHandler()
const items = errorHandler.wrapService(useApiService(TodoItemService))
const title = ref('')

async function create() {
  const item = await items.create<ICreateTodoItem, ITodoItemDefault>({
    listId: props.listId,
    title: title.value,
    content: '',
  })
  title.value = ''
  emit('created', item)
}
</script>

<template>
  <form @submit.prevent="create">
    <input v-model="title" placeholder="New item" />
    <button type="submit">Add</button>
  </form>
</template>
