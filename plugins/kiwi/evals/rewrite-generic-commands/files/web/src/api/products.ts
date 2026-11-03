export interface ProductPatch {
  name?: string
  price?: number
  description?: string | null
  archived?: boolean
}

export async function updateProduct(id: string, patch: ProductPatch) {
  const response = await fetch(`/Product/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  })
  return response.json()
}

export async function createProduct(categoryId: string, name: string, price: number) {
  const response = await fetch('/Product', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ categoryId, name, price }),
  })
  return response.json()
}
