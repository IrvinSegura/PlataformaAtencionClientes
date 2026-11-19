// A page of a list the operator API returns

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
