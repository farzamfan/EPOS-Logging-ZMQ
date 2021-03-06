SELECT MIN(epoch) FROM pss;
SELECT * FROM pss ORDER BY seq_id DESC LIMIT 10;

SELECT * FROM pss WHERE peer = 10 AND epoch = 1547836025 ORDER BY seq_id DESC LIMIT 10;

-- show rows with duplicates
SELECT * FROM pss WHERE has_duplicates IS TRUE ORDER BY seq_id DESC LIMIT 10;

-- average number of peers in view
SELECT
    num_fingers
    ,COUNT(*)
FROM
    pss
WHERE
    -- allow sytem to warmup a bit
    epoch >= (1547835999 + 20)
    
GROUP BY
    num_fingers
ORDER BY
    num_fingers
;

-- check partitioning
--ANALYZE pss;
--EXPLAIN ANALYZE
SELECT * FROM pss WHERE peer = 4;

INSERT INTO pss(dt,peer,network,epoch,thread_id,func,num_fingers,fingers) VALUES ( '2019-01-18 15:00:29', 10, 0, 1547820029, 19, 'runPassiveState', 20, '(tcp://127.0.0.1:63057, 0.5533818927597023)|(tcp://127.0.0.1:51265, 0.5533818927597023)|(tcp://127.0.0.1:55348, 0.5533818927597023)|(tcp://127.0.0.1:51265, 0.5533818927597023)|(tcp://127.0.0.1:65505, 0.5533818927597023)|(tcp://127.0.0.1:61549, 0.5533818927597023)|(tcp://127.0.0.1:58551, 0.5533818927597023)|(tcp://127.0.0.1:50299, 0.5533818927597023)|(tcp://127.0.0.1:63796, 0.5533818927597023)|(tcp://127.0.0.1:54821, 0.5533818927597023)|(tcp://127.0.0.1:55348, 0.5533818927597023)|(tcp://127.0.0.1:60334, 0.5533818927597023)|(tcp://127.0.0.1:54285, 0.5533818927597023)|(tcp://127.0.0.1:65505, 0.5533818927597023)|(tcp://127.0.0.1:60334, 0.5533818927597023)|(tcp://127.0.0.1:54285, 0.5533818927597023)|(tcp://127.0.0.1:50299, 0.5533818927597023)|(tcp://127.0.0.1:54821, 0.5533818927597023)|(tcp://127.0.0.1:58551, 0.5533818927597023)|(tcp://127.0.0.1:63796, 0.5533818927597023)|' )