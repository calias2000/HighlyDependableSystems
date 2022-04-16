package pt.tecnico.bank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RespCollector {

    final List<Object> responses = Collections.synchronizedList(new ArrayList<>());
}

