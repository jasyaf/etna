package org.etnaframework.core.util;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * <pre>
 * This is a fast double-ended circular queue.
 * For performance reasons the class is not synchronized for thread safty the user needs to synchronized the access to its CircularQueue object.
 *
 * 抄自：
 * http://www.codeforge.cn/read/178902/CircularQueue.java__html
 * http://www.koders.com/java/fid0C8B64975D8AF4E5926D47A3FDA350ED3A5C2286.aspx
 * phex_2.1.4.80源码，phex是一款使用java编写得，基于Gnutella协议的p2p软件
 *
 * 修改：
 * 1.增加了泛型支持
 * 2.通过setMaxSize() 可以修改Queue Size,内部发现需要收缩则进行shrink
 *
 * 还可参考：
 * 顺序队列(sequence queue)
 * http://blog.csdn.net/acb0y/article/details/5671395
 * http://sjjp.tjuci.edu.cn/sjjg/datastructure/ds/web/zhanhuoduilie/zhanhuoduilie3.2.2.1.htm
 */
// TODO to represent a framework class this should maybe implement the List interface.
public class CircularQueue<E> {

    public static void main(String[] args) {
        CircularQueue<Integer> a = new CircularQueue<Integer>(8);
        for (int i = 1; i <= 234; i++) {
            a.addToTail(i);
        }
        // a.removeFromHead();
        // a.removeFromTail();
        a.setMaxSize(5);
        // System.out.println(a.getSize());
        for (Iterator<Integer> iterator = a.iterator(); iterator.hasNext(); ) {
            Integer type = iterator.next();
            System.out.println(type);
        }
    }

    /**
     * The size of the queue. There is always one unused element in the queue.
     */
    private int size;

    /**
     * The queue contents.
     */
    private Object[] elements;

    /**
     * The head index of the queue.
     */
    private int headIdx;

    /**
     * The tailhead index of the queue.
     */
    private int tailIdx;

    /**
     * The minimum maxSize is 1. Creates a circularQueue with a initial size of 10.
     */
    public CircularQueue(int maxSize) {
        this(Math.min(10, maxSize), maxSize);
    }

    /**
     * Creates a CircularQueue with a initialSize and a maxSize. The queue is dynamicaly expanded until maxSize is reached. This behaivor is usefull for large queues that might not always get totaly
     * filled. For queues that are always filled it is adviced to set initialSize and maxSize to the same value. The minimum maxSize is 1.
     */
    public CircularQueue(int initialSize, int maxSize) {
        // this is asserted
        if (maxSize < 1) {
            throw new RuntimeException("Min size of the CircularQueue is 1");
        }

        size = maxSize + 1;
        elements = new Object[initialSize + 1];
        headIdx = tailIdx = 0;
    }

    /**
     * Adds a object to the tail of the queue. If the queue is full a element from the head is dropped to free space.
     */
    @SuppressWarnings("unchecked")
    public E addToTail(Object obj) {
        // logQueue();
        Object dropObj = null;
        if (isFull()) {// drop the head element
            dropObj = removeFromHead();
        }
        ensureCapacity();
        elements[tailIdx] = obj;
        tailIdx = nextIndex(tailIdx);
        // logQueue();
        return (E) dropObj;
    }

    public void setMaxSize(int maxSize) {
        int newSize = maxSize + 1;
        // boolean shrink = newSize < size; // 2012-10-13 原来的应该判断是否shrink判断错了，要判断真实的elements的size
        boolean shrink = newSize < elements.length;
        if (shrink) {
            Object[] newElements = new Object[newSize];
            int newHeadIdx = tailIdx - maxSize;
            if (newHeadIdx >= 0) {
                System.arraycopy(elements, newHeadIdx, newElements, 0, newSize);
                this.headIdx = headIdx > tailIdx ? 0 : headIdx;
            } else {
                System.arraycopy(elements, 0, newElements, newSize - (tailIdx + 1), tailIdx + 1);
                System.arraycopy(elements, elements.length + newHeadIdx, newElements, 0, maxSize - tailIdx);
                this.headIdx = headIdx > tailIdx ? 0 : maxSize - tailIdx + headIdx;
            }
            this.tailIdx = maxSize;
            this.size = newSize;
            this.elements = newElements;
        } else {
            this.size = newSize;
            ensureCapacity();
        }
    }

    /**
     * Adds a object to the head of the queue. If the queue is full a element from the tail is dropped to free space. The dropped element is returned.
     */
    @SuppressWarnings("unchecked")
    public E addToHead(Object obj) {
        // logQueue();
        Object dropObj = null;
        if (isFull()) {// drop the head element
            dropObj = removeFromTail();
        }
        ensureCapacity();
        headIdx = prevIndex(headIdx);
        elements[headIdx] = obj;
        // logQueue();
        return (E) dropObj;
    }

    /**
     * Clears the queue. Afterwards no elements from the queue can be accessed anymore.
     */
    public void clear() {
        headIdx = 0;
        tailIdx = 0;

        // Let gc do its work
        for (int i = 0; i < elements.length; ++i) {
            elements[i] = null;
        }
    }

    /**
     * Returns the head element of the queue.
     *
     * @throws NoSuchElementException if queue is empty.
     */
    @SuppressWarnings("unchecked")
    public E getFirst() throws NoSuchElementException {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return (E) elements[headIdx];
    }

    /**
     * Returns the tail element of the queue.
     *
     * @throws NoSuchElementException if queue is empty.
     */
    @SuppressWarnings("unchecked")
    public E getLast() throws NoSuchElementException {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        // adjust last index...
        int index = prevIndex(tailIdx);
        return (E) elements[index];
    }

    /**
     * Returns the element at index in the queue.
     *
     * @throws IndexOutOfBoundsException if index is out of range
     */
    @SuppressWarnings("unchecked")
    public E get(int index) throws IndexOutOfBoundsException {
        int idx = mapIndex(index);
        return (E) elements[idx];
    }

    /**
     * Returns the number of elements in the queue.
     */
    public int getSize() {
        if (headIdx <= tailIdx) {
            // H T
            // [ |x|x|x| | ]
            // 0 1 2 3 4 5
            return tailIdx - headIdx;
        }
        // T H
        // [x| | | |x|x]
        // 0 1 2 3 4 5
        return elements.length - headIdx + tailIdx;
    }

    /**
     * Returns the maximum number of elements this queue can hold.
     */
    public int getCapacity() {
        return size - 1;
    }

    /**
     * Returns true is the queue is empty.
     */
    public boolean isEmpty() {
        return headIdx == tailIdx;
    }

    /**
     * Returns true if the queue is full.
     */
    public boolean isFull() {
        if (elements.length == size) {// the queue is fully expanded
            return nextIndex(tailIdx) == headIdx;
        }
        return false;
    }

    /**
     * Removes and returns the element on the head of the queue
     *
     * @throws NoSuchElementException if queue is empty.
     */
    @SuppressWarnings("unchecked")
    public E removeFromHead() throws NoSuchElementException {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        Object obj = elements[headIdx];
        elements[headIdx] = null;
        headIdx = nextIndex(headIdx);
        return (E) obj;
    }

    /**
     * Removes and returns the element on the tail of the queue
     *
     * @throws NoSuchElementException if queue is empty.
     */
    @SuppressWarnings("unchecked")
    public E removeFromTail() throws NoSuchElementException {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        tailIdx = prevIndex(tailIdx);
        Object obj = elements[tailIdx];
        elements[tailIdx] = null;
        return (E) obj;
    }

    public Iterator<E> iterator() {
        return new CircularQueueIterator();
    }

    private void ensureCapacity() {
        if (elements.length == size) {
            return;
        }
        if (nextIndex(tailIdx) != headIdx) {
            return;
        }
        // expand array and copy over
        int newSize = Math.min(elements.length * 2, size);
        Object[] newElements = new Object[newSize];
        if (headIdx <= tailIdx) {
            // H T
            // [ |x|x|x| | ]
            // 0 1 2 3 4 5
            System.arraycopy(elements, headIdx, newElements, headIdx, tailIdx - headIdx);
        } else {
            // T H
            // [x| | | |x|x]
            // 0 1 2 3 4 5
            int newHeadIdx = newSize - (elements.length - headIdx);
            if (tailIdx > 0) {
                System.arraycopy(elements, 0, newElements, 0, tailIdx);// 拷贝个数应该是tailIdx个， 而不是tailIdx-1个
            }
            System.arraycopy(elements, headIdx, newElements, newHeadIdx, elements.length - headIdx);
            headIdx = newHeadIdx;
        }
        elements = newElements;
    }

    /**
     * Maps the given index into the index in the internal array.
     */
    private int mapIndex(int index) throws IndexOutOfBoundsException {
        if (index >= elements.length || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + elements.length);
        }
        return (index + headIdx) % elements.length;
    }

    /**
     * Gets the next index for the given index.
     */
    private synchronized int nextIndex(int idx) {
        if (idx == elements.length - 1) {
            return 0;
        }
        return idx + 1;
    }

    /**
     * Gets the previous index for the given index.
     */
    private synchronized int prevIndex(int idx) {
        if (idx == 0) {
            return elements.length - 1;
        }
        return idx - 1;
    }

    /*
     * private void logQueue() { System.out.println( "-------------------------------" ); System.out.println( headIdx + " " + tailIdx + " " + getSize() ); System.out.print( "[ " ); for( int i = 0; i <
     * elements.length; i++ ) { System.out.print( elements[i] + " | " ); } System.out.println( " ]" ); System.out.println( "-------------------------------" ); }
     */

    private class CircularQueueIterator implements Iterator<E> {

        /**
         * Store originalHead to check for concurent modifications.
         */
        int originalHead;

        /**
         * Store originalTail to check for concurent modifications.
         */
        int originalTail;

        /**
         * Next element index.
         */
        int nextElement;

        public CircularQueueIterator() {
            nextElement = headIdx;
            originalHead = headIdx;
            originalTail = tailIdx;
        }

        @Override
        public boolean hasNext() {
            checkForComodification();
            return nextElement != tailIdx;
        }

        @Override
        @SuppressWarnings("unchecked")
        public E next() throws NoSuchElementException {
            checkForComodification();
            if (nextElement == tailIdx) {
                throw new NoSuchElementException();
            }

            Object obj = elements[nextElement];
            nextElement = nextIndex(nextElement);
            return (E) obj;
        }

        /**
         * This operation is not supported.
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void checkForComodification() {
            if (originalHead != headIdx || originalTail != tailIdx) {
                throw new ConcurrentModificationException();
            }
        }
    }
}
