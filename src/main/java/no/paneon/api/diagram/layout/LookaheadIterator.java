package no.paneon.api.diagram.layout;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;

class LookaheadIterator<T> implements Iterator<T> {

	private T next;
	private Optional<T> current;

	private Iterator<T> iter;

	private boolean noSuchElement;

	public LookaheadIterator(Iterator<T> iterator) {
		noSuchElement = false;
		iter = iterator;
		current = Optional.empty();
		next = null;
		advanceIterator();
	}

	public T peek() {
		return next;
	}

	@Override
	public T next() {
		if(noSuchElement) {
			throw new NoSuchElementException();
		} else {
			T res = next; 	
			advanceIterator(); 
			return res;
		}
	}

	@Override
	public boolean hasNext() {
		return !noSuchElement;
	}

	public Optional<T> current() {
		return current;
	}
	
	private void advanceIterator() {
		if(next!=null) current = Optional.of(next);
		if (iter.hasNext()) {
			next = iter.next();
		} else {
			noSuchElement = true;
		}
	}
}
