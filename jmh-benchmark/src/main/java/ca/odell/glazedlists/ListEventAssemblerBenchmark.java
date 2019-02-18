package ca.odell.glazedlists;

import ca.odell.glazedlists.event.IListEventAssembler;
import ca.odell.glazedlists.event.ListEventAssembler;
import org.openjdk.jmh.annotations.*;

import java.util.Collections;

@State(Scope.Benchmark)
public class ListEventAssemblerBenchmark {
  private BasicEventList<String> list;
  private ListEventAssembler<String> assembler;

  @Setup
  public void setUp() {
    list = new BasicEventList<>();
    assembler = new ListEventAssembler<>(list, IListEventAssembler.createListEventPublisher());
  }

  @Benchmark
  @Warmup(iterations = 3)
  @Measurement(iterations = 10)
  @Fork(3)
  public void testDeleteUpdate() {
    assembler.beginEvent();
    assembler.elementDeleted(0, Collections.nCopies(10, "X"));
    assembler.elementUpdated(4, Collections.nCopies(4, "X"), Collections.nCopies(4, "Y"));
    assembler.discardEvent();
  }
}
