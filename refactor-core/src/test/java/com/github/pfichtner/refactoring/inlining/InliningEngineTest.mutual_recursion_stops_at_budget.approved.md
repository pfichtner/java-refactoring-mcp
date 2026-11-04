# Aggressive inline: mutual recursion stopped by budget


### Input:
```java
public class Mutual {
    public int start() {
        return first(3);
    }

    private int first(int value) {
        if (value <= 0) {
            return 0;
        }
        return second(value - 1);
    }

    private int second(int value) {
        if (value <= 0) {
            return 0;
        }
        return first(value - 1);
    }
}
```

### Refactoring:
**inline all** `first`, `second`  
mutual recursion keeps producing candidates; the maxTransformations budget stops it

### Output:
```java
public class Mutual {
    public int start() {
        return first(3);
    }

    private int first(int value) {
        if (value <= 0) {
            return 0;
        }
        int __inline_value1 = value - 1;
		int __inline_result1;
		__inline_label1: {
			if (__inline_value1 <= 0) {
				{
					__inline_result1 = 0;
					break __inline_label1;
				}
			}
			int __inline_value = __inline_value1 - 1;
			int __inline_result;
			__inline_label: {
				if (__inline_value <= 0) {
					{
						__inline_result = 0;
						break __inline_label;
					}
				}
				{
					int __inline_value2 = __inline_value - 1;
					int __inline_result2;
					__inline_label2: {
						if (__inline_value2 <= 0) {
							{
								__inline_result2 = 0;
								break __inline_label2;
							}
						}
						int __inline___inline_value = __inline_value2 - 1;
						int __inline___inline_result;
						__inline_label___inline_label: {
							if (__inline___inline_value <= 0) {
								{
									__inline___inline_result = 0;
									break __inline_label___inline_label;
								}
							}
							{
								int __inline_value3 = __inline___inline_value - 1;
								int __inline_result3;
								__inline_label3: {
									if (__inline_value3 <= 0) {
										{
											__inline_result3 = 0;
											break __inline_label3;
										}
									}
									int __inline___inline_value1 = __inline_value3 - 1;
									int __inline___inline_result1;
									__inline_label___inline_label1: {
										if (__inline___inline_value1 <= 0) {
											{
												__inline___inline_result1 = 0;
												break __inline_label___inline_label1;
											}
										}
										{
											__inline___inline_result1 = second(__inline___inline_value1 - 1);
											break __inline_label___inline_label1;
										}
									}
									{
										__inline_result3 = __inline___inline_result1;
										break __inline_label3;
									}
								}
								__inline___inline_result = __inline_result3;
								break __inline_label___inline_label;
							}
						}
						{
							__inline_result2 = __inline___inline_result;
							break __inline_label2;
						}
					}
					__inline_result = __inline_result2;
					break __inline_label;
				}
			}
			{
				__inline_result1 = __inline_result;
				break __inline_label1;
			}
		}
		return __inline_result1;
    }

    private int second(int value) {
        if (value <= 0) {
            return 0;
        }
        int __inline_value = value - 1;
		int __inline_result;
		__inline_label: {
			if (__inline_value <= 0) {
				{
					__inline_result = 0;
					break __inline_label;
				}
			}
			{
				__inline_result = second(__inline_value - 1);
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```