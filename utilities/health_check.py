import os
from datetime import datetime
from urllib.request import Request, urlopen

SITE = os.environ['site']  # URL of the site to check, stored in the site environment variable


def lambda_handler(event, context):
    print(f'Checking {SITE} at {event["time"]}...')
    try:
        req = Request(SITE, headers={'User-Agent': 'AWS Lambda'})
        response = urlopen(req)
        if response.status != 200:
            raise Exception('Validation failed')
    except:
        print('Check failed!')
        raise
    else:
        print('Check passed!')
        return event['time']
    finally:
        print(f'Check complete at {str(datetime.now())}')
