import { basicCrudService } from '@werdenktwas/kiwi-vue/services'
import type { ITodoItemDefault, ITodoListDefault, TodoItemId, TodoListId } from '@/api/demo.model'

export class TodoListService extends basicCrudService<ITodoListDefault, TodoListId>('TodoList') {}

export class TodoItemService extends basicCrudService<ITodoItemDefault, TodoItemId>('TodoItem') {}
