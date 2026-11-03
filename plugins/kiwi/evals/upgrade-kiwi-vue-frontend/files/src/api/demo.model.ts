/**
 * -- Kiwi Framework generated Fieldset types --
 * Generated on: 2026-03-02T09:14:21.502113Z
 * Fieldsets: TodoItemFieldsets, TodoListFieldsets
 * Create commands: CreateTodoItem, CreateTodoList
 * Update commands: UpdateTodoItem, UpdateTodoList
 */

declare const idType: unique symbol;
export type IdType<ID extends string, T = string> = T & { [idType]: ID };

// --- Entity Schema ---

export interface ITodoItem {
  readonly id?: TodoItemId;
  readonly listId?: TodoListId;
  readonly title?: string;
  readonly content?: string;
  readonly status?: ("Open" | "Done");
  readonly order?: number;
  readonly list?: ITodoList;
}

export type TodoItemId = IdType<"TodoItemId">;
export type TodoListId = IdType<"TodoListId">;

export interface ITodoList {
  readonly id?: TodoListId;
  readonly title?: string;
  readonly author?: (null | string);
  readonly items?: ReadonlyArray<ITodoItem>;
}

// --- Available Fieldsets ---

export interface ITodoItemDefault {
  readonly id: TodoItemId;
  readonly listId: TodoListId;
  readonly title: string;
  readonly content: string;
  readonly status: ("Open" | "Done");
  readonly order: number;
}

export interface ITodoItemOrder {
  readonly id: TodoItemId;
  readonly order: number;
}

export interface ITodoItemShort {
  readonly id: TodoItemId;
  readonly title: string;
  readonly content: string;
  readonly status: ("Open" | "Done");
  readonly order: number;
}

export type ITodoItemFieldsets = {
  readonly "default": ITodoItemDefault
  readonly "order": ITodoItemOrder
  readonly "short": ITodoItemShort
};

export interface ITodoListDefault {
  readonly id: TodoListId;
  readonly title: string;
  readonly author: (null | string);
}

export interface ITodoListDetails {
  readonly id: TodoListId;
  readonly title: string;
  readonly author: (null | string);
  readonly items: ReadonlyArray<ITodoItemShort>;
}

export interface ITodoListItemsOrder {
  readonly id: TodoListId;
  readonly items: ReadonlyArray<ITodoItemOrder>;
}

export type ITodoListFieldsets = {
  readonly "default": ITodoListDefault
  readonly "details": ITodoListDetails
  readonly "itemsOrder": ITodoListItemsOrder
};

// --- Entity Commands ---

export interface ICreateTodoItem {
  listId: TodoListId;
  title: string;
  content: string;
}

export interface ICreateTodoList {
  title: string;
}

export interface IUpdateTodoItem {
  title: string;
  content: string;
  status: ("Open" | "Done");
  order: number;
}

export interface IUpdateTodoList {
  title: string;
}
