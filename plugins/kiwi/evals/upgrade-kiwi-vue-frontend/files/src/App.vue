<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  consoleErrorConsumer,
  i18nErrorTransformer,
  provideApi,
  provideApiErrorHandler,
} from '@werdenktwas/kiwi-vue/services'
import type { TodoListId } from '@/api/demo.model'
import { apiUrl, credentials } from '@/stores/session'
import TodoListView from '@/components/TodoListView.vue'

const { tm, rt } = useI18n()

provideApi({ apiUrl, credentials })
provideApiErrorHandler({
  transform: i18nErrorTransformer({ tm, rt, messagesKey: 'errors' }),
  consumers: [({ formattedMessage }) => window.alert(formattedMessage), consoleErrorConsumer],
})

const listId = new URLSearchParams(window.location.search).get('list') as TodoListId
</script>

<template>
  <TodoListView :list-id="listId" />
</template>
