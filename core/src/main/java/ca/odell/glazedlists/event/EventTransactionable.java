package ca.odell.glazedlists.event;

import ca.odell.glazedlists.EventList;

import java.util.function.Consumer;

public interface EventTransactionable<E> {
  void transaction(Consumer<EventList<E>> consumer);

  public static <E> EventTransactionable<E> create(EventList<E> list){
    return create(list, null);
  }

  public static <E> EventTransactionable<E> create(EventList<E> list, ListEventAssembler<E> assembler){
    return c -> {
      if(assembler != null) {
        assembler.beginEvent();
      }
      try{
        c.accept(list);
      } finally {
        if(assembler != null) {
          assembler.commitEvent();
        }
      }
    };
  }
}
