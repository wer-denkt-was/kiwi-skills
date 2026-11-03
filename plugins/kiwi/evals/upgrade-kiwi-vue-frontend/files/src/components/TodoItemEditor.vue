<script setup lang="ts">
import { ref } from 'vue'
import { useApiErrorHandler, useApiService } from '@werdenktwas/kiwi-vue/services'
import type { ITodoItemDefault, IUpdateTodoItem } from '@/api/demo.model'
import { TodoItemService } from '@/services/todo.services'

const props = defineProps<{ item: ITodoItemDefault }>()
const emit = defineEmits<{ updated: [item: ITodoItemDefault] }>()

const errorHandler = useApiErrorHandler()
const items = errorHandler.wrapService(useApiService(TodoItemService))
const title = ref(props.item.title)
const content = ref(props.item.content)

async function save() {
  const updated = await items.update<IUpdateTodoItem, ITodoItemDefault>(props.item.id, {
    ...props.item,
    title: title.value,
    content: content.value,
  })
  emit('updated', updated)
}

async function toggleDone() {
  const status = props.item.status === 'Done' ? 'Open' : 'Done'
  const updated = await items.update<IUpdateTodoItem, ITodoItemDefault>(props.item.id, { ...props.item, status })
  emit('updated', updated)
}
</script>

<template>
  <div class="todo-item">
    <input type="checkbox" :checked="item.status === 'Done'" @change="toggleDone" />
    <input v-model="title" />
    <textarea v-model="content" />
    <button @click="save">Save</button>
  </div>
</template>
