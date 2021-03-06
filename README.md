# JDrum

## Intro

Java implementation of the disk repository with update management (DRUM) key-value store framework as presented by 
Hsin-Tsang Lee, Derek Leonard, Xiaoming Wang, and Dmitri Loguinov in the paper "IRLbot: Scaling to 6 Billion Pages and 
Beyond".

DRUM divides data received via its `check`, `checkUpdate` or `update` method into a number of buckets and stores them 
efficiently with the help of bucket sort into bucket disk files. If a disk bucket file exceeds a certain threshold a 
merge with the backing data store is requested. Upon merge, all disk bucket files are merged with the data store 
one-by-one in a single-pass operation.

On merge time a `check` operation will classify if an entry with the provided key is available in the data store and 
therefore return `DUPLICATE_KEY` or `UNIQUE_KEY` if it is not yet available. An `update` operation will either add new 
or update existing data to the backing data store while a `checkUpdate` operation will combine both operations.

As data is first stored in an in-memory buffer then written to a disk bucket file and finally merged into the backing
data store, responses are dispatched asynchronously via a `Dispatcher` interface.

This implementation extends the original architecture with an `appendUpdate` operation which appends data to the value
segment of the key/value tuple. 

As of now only a self-written `DataStore` and a `Berkeley DB` key-value store are supported by JDrum.

## Usage

### Initialization

To initialize a new instance of DRUM the API provides a `DrumBuilder`.

```java
public class DrumTest implements DrumListener
{
    public void testDrum() throws DrumException, IOException
    {
        Drum<V, A> drum = new DrumBuilder<>("keyValueName", V.class, A.class)
                                           .numBuckets(4).bufferSize(64)
                                           .dispatcher(new LogFileDispatcher<>())
                                           .listener(this)
                                           .datastore(SimpleDataStoreMerger.class)
                                           .build();
        ...
        
        drum.dispose();
    }
    
    @Override
    public update(DrumEvent<? extends DrumEvent<?>> event) 
    {
        System.out.println("DrumEvent: " + event);
    }
    
    ...
}
```

As the API makes use of the default or a customized serialization mechanism, `V` and `A`, which are the types for values 
and auxiliary data attached to the key/value tuple, need to implement either `Serializable`, `ByteSerializable<T>` or
 `AppendableData<T>`. The first interface is the default Java `Serializable` interface while the latter two are custom ones
 which were introduced to minimize the byte load written to files to a bare minimum.
 
The builder provides a series of configuration options. The only ones required are the constructor-parameters which 
specify the instance name of the JDrum key/value store and the expected classes for value and auxiliary data objects.

| Method                      | Explanation                                                                               | Default |
| --------------------------- | ----------------------------------------------------------------------------------------- | ------- |
| numBuckets(int)             | The number of buckets JDrum will use. This should be a power of 2 (e.g. 2, 4, 8, 16, ...) | 512     |
| bufferSize(int)             | The size in bytes a buffers has to reach before it requests a merge. This has to be a power of 2      | 64kb    |
| listener(DrumListener)      | Assigns an object to JDrum which gets notified on internal state changes like the filling up of buffers or disk files or on the current state of each disk bucket | null |
| dispatcher(Dispatcher)      | An object implementing this interface which will receive responses to the invoked operation like f.e. `UNIQUE_KEY` or `DUPLICATE_KEY` classifications and/or the current value of an updated object | NullDispatcher |
| datastore(DataStoreMerger) | A factory method to initialize a merger instance which takes care of merging data from the respective bucket files to a backing data store. By default this project ships with the `SimpleDataStoreMerder`, which merges data into a backing file. A `BerkeleyDBStoreMerger` is available in the [jdrum-datastore-berkeley](https://github.com/RovoMe/jdrum-datastore-berkeley) Github project | null

### Insert or update data

Once a JDrum instance is initialized, data can be stored or updated in the underlying data store using one of the 
following operations:

* `drum.update(Long, V);`
* `drum.update(Long, V, A);`
* `drum.appendUpdate(Long, V);`
* `drum.appendUpdate(Long, V, A);`
* `drum.checkUpdate(Long, V);`
* `drum.checkUpdate(Long, V, A);`

The first parameter is always a 64-bit long key, which can be generated using `DrumUtils.hash(String)` or `DrumUtils.hash(Object)`, 
while the second parameter is the value which should be stored into the data store. The third parameter is optional and 
allows to add additional data to the key/value. This auxiliary data can therefore be some meta data about the key/value 
pair or the current state of the environment when the invocation occurred or simply data you want to get back once the 
dispatcher returned results.

Keep in mind that the auxiliary data is stored in a separate bucket file and not merged with the data store.

All operations have in common that they will create a new key/value entry in the data store if the given key (first 
parameter) could not be found. The first two operation will however replace an existing value for a found key while the
second two operations will append the value to the value matching the found key in the backing data store. In order to
append the state of the value object to the existing one, the value object needs to implement the `AppendableData` 
interface. The last two operations perform a regular `update(...)` operation but will also return a classification
result regarding the uniqueness of the key via the dispatcher.

The example code below showcases a more complex scenario where a custom bean is used to store and append data to the 
data store. This sample makes use of a custom `PLDData` bean which is taken from the JIRLbot project and described in
short further below.

```java
public class DrumTest extends NullDispatcher<PLDData, String>
{
    public void testDrum() throws DrumException, IOException
    {
        Drum<PLDData, String> drum = 
                new DrumBuilder<>("pldIndegree", PLDData.class, String.class)
                                 .numBuckets(4)
                                 .bufferSize(64)
                                 .dispatcher(this)
                                 .datastore(SimpleDataStoreMerger.class)
                                 .build();
        ...
        
        String url = "https://github.com/RovoMe/JDrum";
        PLDData data1 = new PLDData();
        data1.setHash(1);
        Set<Long> neighbor1 = new TreeSet<>();
        neighbor1.add(7L);
        neighbor1.add(3L);
        data1.setIndegreeNeighbors(neighbor1);
        
        drum.update(DrumUtils.hash(url), data1, url);
        
        PLDData data2 = new PLDData();
        data2.setHash(1);
        Set<Long> neighbor2 = new TreeSet<>();
        neighbor2.add(7L);
        neighbor2.add(4L);
        data2.setIndegreeNeighbors(neighbor2);
        
        drum.appendUpdate(DrumUtils.hash(url), data2, url);
        
        ...
        
        drum.dispose();
    }
    
    ...
    
    // get the results delivered once the data is merged with the backing data store
    
    @Override
    public void update(Long key, PLDData data, String aux)
    {
        ...
        
        // will receive 2 messages upon data merge
        // key: 1, data[hash: 1, neighbors: {3, 7}], aux = "https://github.com/RovoMe/JDrum"
        // key: 1, data[hash: 1, neighbors: {3, 4, 7}], aux = "https://github.com/RovoMe/JDrum"
    }
}
```

### Check key for uniqueness

To query JDrum for existing keys simply invoke `check(Long)` on the JDrum instance or attach some auxiliary data to the
request which gets returned on dispatch time in order to keep the context the request was issued for.

```java
String url = "https://github.com/RovoMe/JDrum";
drum.check(DrumUtils.hash(url));
drum.check(DrumUtils.hash(url), url);
```

The code above will check if the 64-bit hash code for the given URL is already known by the JDrum instance or not. The
class specified as dispatcher while instantiating JDrum will receive a `UNIQUE_KEY` or `DUPLICATE_KEY` notification when
the merge-phase of the disk bucket files actually occurred.

A simple dispatcher which just prints the output to the console could look like this: 

```java
public class ConsoleDispatcher<V extends Serializable, A extends Serializable> extends NullDispatcher<V, A>
{
    @Override
    public void uniqueKeyCheck(Long key)
    {
        System.out.println("UniqueKey: " + key);
    }

    @Override
    public void uniqueKeyCheck(Long key, A aux)
    {
        System.out.println("UniqueKey: " + key + " Aux: " + aux);
    }
    
    @Override
    public void duplicateKeyCheck(Long key)
    {
        System.out.println("DuplicateKey: " + key);
    }

    @Override
    public void duplicateKeyCheck(Long key, A aux)
    {
        System.out.println("DuplicateKey: " + key + " Aux: " + aux);
    }
}
```

### Creating own serializable data

JDrum supports the default serialization mechanism offered by Java, though this creates a lot more byte and overhead
then the data actually requires as Java will serialize the class name and other meta data as well, even on utilizing
`writeObject(ObjectOutputStream)` and `readObject(ObjectInputStream)`. To tackle this issue, JDrum provides two further
interfaces: `ByteSerializable<T>` and `AppendableData<T>`.

The former one is a `Serializable` replacement which also extends this interface and requires an implementation to 
define two methods `public byte[] toBytes() { ... }` and `public T readBytes(byte[] bytes)`. The first method here will
transform the current state of an object to bytes whereas the second method will take a byte array and convert it to a
new instance of this class.

`AppendableData<T>` extends `ByteSerializable<T>` by a further method `public void append(T data)` which will append
the internal state stored in the provided data object to the object the method was invoked on. Here an implementing 
object should merge the given data into its current state.

Below is a small excerpt from the `PLDData` bean used in the JIRLbot framework:

```java
public class PLDData implements AppendableData<PLDData>, Comparable<PLDTestData>
{
    private long hash = 0;
    private int budget = 0;
    private Set<Long> indegreeNeighbors = null;
    
    // getter and setter omitted
    
    // append state from other object
    @Override
    public void append(PLDData data)
    {
        if (this.indegreeNeighbors==null)
        {
            this.indegreeNeighbors = new TreeSet<>();
        }
        if (data != null)
        {
            this.indegreeNeighbors.addAll(data.getIndegreeNeighbors());
        }
    }
    
    // 8 bytes for the long key
    // 4 bytes for the length of the indegreeNeighbors set
    // 8 * indegreeNeighbors.size bytes for each contained long key of other PLDs
    // 4 bytes for the int value of the budget
    
    // serialize object to bytes
    @Override
    public byte[] toBytes()
    {
        int size = 12 + 8 * this.indegreeNeighbors.size() + 4;
        byte[] totalBytes = new byte[size];
        byte[] keyBytes = DrumUtils.long2bytes(this.hash); // 8 bytes
        System.arraycopy(keyBytes, 0, totalBytes, 0, 8);
        byte[] neighborSize = DrumUtils.int2bytes(this.indegreeNeighbors.size());
        System.arraycopy(neighborSize, 0, totalBytes, 8, 4); // 4 bytes
        int pos = 12;
        for (Long neighbor : this.indegreeNeighbors)
        {
            byte[] neighborBytes = DrumUtils.long2bytes(neighbor);
            System.arraycopy(neighborBytes, 0, totalBytes, pos, 8);
            pos += 8;
        }
        byte[] budget = DrumUtils.int2bytes(this.budget);
        System.arraycopy(budget, 0, totalBytes, pos, 4); // 4 bytes

        return totalBytes;
    }

    // deserialize byte to object
    @Override
    public PLDData readBytes(byte[] bytes)
    {
        byte[] keyBytes = new byte[8];
        System.arraycopy(bytes, 0, keyBytes, 0, 8);
        long hash = DrumUtils.byte2long(keyBytes);

        byte[] valueSizeBytes = new byte[4];
        System.arraycopy(bytes, 8, valueSizeBytes, 0, 4);
        int valueSize = DrumUtils.bytes2int(valueSizeBytes);

        TreeSet<Long> indegreeNeighbors = new TreeSet<>();

        int pos = 12;
        for (int i = 0; i < valueSize; i++)
        {
            byte[] valueBytes = new byte[8];
            System.arraycopy(bytes, pos, valueBytes, 0, 8);
            indegreeNeighbors.add(DrumUtils.byte2long(valueBytes));
            pos += 8;
        }

        byte[] budgetBytes = new byte[4];
        System.arraycopy(bytes, pos, budgetBytes, 0, 4);
        int budget = DrumUtils.bytes2int(budgetBytes);

        PLDTestData data = new PLDTestData();
        data.setHash(hash);
        data.setIndegreeNeighbors(indegreeNeighbors);
        data.setBudget(budget);

        return data;
    }
    
    @Override
    public int compareTo(PLDTestData o)
    {
        if (this.getHash() < o.getHash())
        {
            return -1;
        }
        else if (this.getHash() > o.getHash())
        {
            return 1;
        }
    
        return 0;
    }
}
```

## Further links

* [Paper: IRLbot: Scaling to 6 Billion Pages and Beyond](http://www2008.org/papers/pdf/p427-leeA.pdf)
* [DRUMS: Disk Repository with Update Management and Select Option](http://mgledi.github.io/DRUMS/)
* [C++ Draft-Implementation](http://www.codeproject.com/Articles/36221/DRUM-A-C-Implementation-for-the-URL-seen-Test-of-a)
