package at.rovo.caching.drum.internal.backend;

import at.rovo.caching.drum.Dispatcher;
import at.rovo.caching.drum.DrumException;
import at.rovo.caching.drum.Merger;
import at.rovo.caching.drum.data.ByteSerializer;
import at.rovo.caching.drum.event.DrumEventDispatcher;
import at.rovo.caching.drum.internal.backend.berkeley.BerkeleyDBStorageFactory;
import at.rovo.caching.drum.internal.backend.cacheFile.CacheFileStorageFactory;

/**
 * A basic Factory class for creating a DRUM backing data storage.
 * <p>
 * Currently only a Berkeley DB and a self written CacheFile storage are available. By default the CacheFile storage can
 * be created through invoking {@link #getDefaultStorageFactory(String, int, at.rovo.caching.drum.Dispatcher, Class,
 * Class, DrumEventDispatcher)}
 *
 * @param <V>
 * 		The type of the value
 * @param <A>
 * 		The type of the auxiliary data attached to a key
 *
 * @author Roman Vottner
 * @see BerkeleyDBStorageFactory
 * @see CacheFileStorageFactory
 */
public abstract class DrumStorageFactory<V extends ByteSerializer<V>, A extends ByteSerializer<A>>
{
	/** The created merger implementation **/
	protected Merger<V, A> merger = null;

	/**
	 * On creating a new instance of this class, it will trigger {@link #create(String, int, Dispatcher, Class, Class, DrumEventDispatcher)}
	 * and therefore delegate the creation of the concrete implementation to its child class.
	 *
	 * @param drumName
	 * 		The name of the DRUM instance
	 * @param numBuckets
	 * 		The number of bucket files used to store the data
	 * @param dispatcher
	 * 		A reference to the {@link at.rovo.caching.drum.Dispatcher} instance that will dispatch the results
	 * @param valueClass
	 * 		The class of the value type
	 * @param auxClass
	 * 		The class of the auxiliary data type
	 * @param eventDispatcher
	 * 		A reference to the {@link DrumEventDispatcher} that will forward certain DRUM events like merge status changed
	 * 		or disk writer events
	 *
	 * @throws DrumException
	 * 		If the backing data store could not be created
	 */
	public DrumStorageFactory(String drumName, int numBuckets, Dispatcher<V, A> dispatcher, Class<? super V> valueClass,
							  Class<? super A> auxClass, DrumEventDispatcher eventDispatcher) throws DrumException
	{
		this.create(drumName, numBuckets, dispatcher, valueClass, auxClass, eventDispatcher);
	}

	/**
	 * Forces a subclass to implement the creation of a new instance of a backing data storage.
	 *
	 * @param drumName
	 * 		The name of the DRUM instance
	 * @param numBuckets
	 * 		The number of bucket files used to store the data
	 * @param dispatcher
	 * 		A reference to the {@link at.rovo.caching.drum.Dispatcher} instance that will dispatch the results
	 * @param valueClass
	 * 		The class of the value type
	 * @param auxClass
	 * 		The class of the auxiliary data type
	 * @param eventDispatcher
	 * 		A reference to the {@link DrumEventDispatcher} that will forward certain DRUM events like merge status changed
	 * 		or disk writer events
	 *
	 * @throws DrumException
	 * 		If the backing data store could not be created
	 */
	protected abstract void create(String drumName, int numBuckets, Dispatcher<V, A> dispatcher,
								   Class<? super V> valueClass, Class<? super A> auxClass,
								   DrumEventDispatcher eventDispatcher) throws DrumException;

	/**
	 * Returns the generated data storage.
	 *
	 * @return The generated data storage
	 */
	public Merger<V, A> getStorage()
	{
		return this.merger;
	}

	/**
	 * Create the default backing data store.
	 *
	 * @param drumName
	 * 		The name of the DRUM instance
	 * @param numBuckets
	 * 		The number of bucket files used to store the data
	 * @param dispatcher
	 * 		A reference to the {@link at.rovo.caching.drum.Dispatcher} instance that will dispatch the results
	 * @param valueClass
	 * 		The class of the value type
	 * @param auxClass
	 * 		The class of the auxiliary data type
	 * @param eventDispatcher
	 * 		A reference to the {@link DrumEventDispatcher} that will forward certain DRUM events like merge status changed
	 * 		or disk writer events
	 *
	 * @return A reference to the default backing data storage, which is the CacheFile data store
	 *
	 * @throws DrumException
	 * 		If the backing data store could not be created
	 */
	public static <V extends ByteSerializer<V>, A extends ByteSerializer<A>> DrumStorageFactory<V, A> getDefaultStorageFactory(
			String drumName, int numBuckets, Dispatcher<V, A> dispatcher, Class<? super V> valueClass,
			Class<? super A> auxClass, DrumEventDispatcher eventDispatcher) throws DrumException
	{
		return new CacheFileStorageFactory<>(drumName, numBuckets, dispatcher, valueClass, auxClass, eventDispatcher);
	}
}
