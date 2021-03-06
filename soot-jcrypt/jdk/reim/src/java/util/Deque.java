package java.util;
import checkers.inference.reim.quals.*;

public interface Deque<E> extends Queue<E> {
  public abstract void addFirst(@Readonly E a1); //WEI
  public abstract void addLast(@Readonly E a1);//WEI
  public abstract boolean offerFirst(E a1);
  public abstract boolean offerLast(E a1);
  public abstract E removeFirst();
  public abstract E removeLast();
  public abstract E pollFirst();
  public abstract E pollLast();
  @PolyreadThis public abstract E getFirst() ; //WEI
  @PolyreadThis public abstract E getLast()  ; //WEI
  @PolyreadThis public abstract E peekFirst() ; //WEI
  @PolyreadThis public abstract E peekLast() ;//WEI
  public abstract boolean removeFirstOccurrence(@Readonly Object a1);
  public abstract boolean removeLastOccurrence(@Readonly Object a1);
  public abstract boolean add(@Readonly E a1); //WEI
  public abstract boolean offer(@Readonly E a1); //WEI
  public abstract E remove();
  public abstract E poll();
  public abstract E element();
  @ReadonlyThis public abstract E peek() ;
  public abstract void push(@Readonly E a1); //WEI
  public abstract E pop();
  public abstract boolean remove(@Readonly Object a1);
  @ReadonlyThis public abstract boolean contains(@Readonly Object a1) ;
  @ReadonlyThis public abstract int size() ;
  @PolyreadThis public abstract @Polyread Iterator<E> iterator() ;
  @PolyreadThis public abstract @Polyread Iterator<E> descendingIterator() ;
}
