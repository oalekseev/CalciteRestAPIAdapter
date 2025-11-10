package org.apache.calcite.adapter.restapi.rest.interfaces;

import net.minidev.json.JSONArray;

public interface ArrayReader {

    JSONArray read(String path);

}
